// Uso: node docs/theorytab-sync.mjs fetch <artist-slug> <song-slug>     → imprime tonalidade + seções (formato do painel)
//      node docs/theorytab-sync.mjs sync <trackId> <artist-slug> <song-slug> → busca, grava via PUT /reference e imprime a comparação
//      node docs/theorytab-sync.mjs batch <arquivo.tsv>                   → linhas "trackId\tartist-slug\tsong-slug", faz sync em cada
import { readFileSync } from 'node:fs';

const API = process.env.ORELHA_API ?? 'http://localhost:8081/api';
const UA = 'Mozilla/5.0 (orelha; reference sync for the owner\'s own account)';

export async function fetchTheorytab(artist, song) {
  const url = `https://www.hooktheory.com/theorytab/view/${artist}/${song}`;
  const res = await fetch(url, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`HTTP ${res.status} em ${url}`);
  const html = await res.text();
  if (/Sign In - Hooktheory/.test(html) && !/About the Chord Progressions/.test(html)) throw new Error('página exige login');
  const key = /data-tonic="([A-G][#b♯♭]?)"\s+data-scale="([a-z]+)"/.exec(html);
  const from = html.indexOf('About the Chord Progressions');
  const area = from >= 0 ? html.slice(from, from + 60000) : html;
  const rowRe = /<span[^>]*>([^<]{2,60})<\/span>\s*<\/td>\s*<td[^>]*>\s*<a href="\/theorytab\/advanced-search\?chordString=([^&"]+)&/g;
  const sections = [];
  let m;
  while ((m = rowRe.exec(area))) {
    const label = m[1].trim();
    const prog = decodeURIComponent(m[2].replace(/\+/g, ' ')).trim();
    sections.push({ label, progression: prog });
  }
  if (sections.length === 0) throw new Error('nenhuma seção encontrada em ' + url);
  const tonic = key ? key[1].replace('♯', '#').replace('♭', 'b') : null;
  const scale = key ? key[2] : null;
  const text = (tonic ? `tonalidade: ${tonic} ${scale}\n` : '') + sections.map((s) => `${s.label}: ${s.progression}`).join('\n');
  return { url, tonic, scale, sections, text };
}

async function sync(trackId, artist, song) {
  const tt = await fetchTheorytab(artist, song);
  const put = await fetch(`${API}/tracks/${trackId}/reference`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text: tt.text, url: tt.url }),
  });
  if (!put.ok) throw new Error(`PUT ${put.status}: ${await put.text()}`);
  const cmp = await (await fetch(`${API}/tracks/${trackId}/reference/compare`)).json();
  return { tt, cmp };
}

function report(trackId, title, { tt, cmp }) {
  const k = cmp.tonicMatches ? (cmp.modeMatches ? 'tom ✓' : 'tom ✓ modo ✗') : 'tom ✗';
  console.log(`#${trackId} ${title} | TT ${tt.tonic} ${tt.scale} | ${k} | seq ${(cmp.sequenceSimilarity * 100).toFixed(0)}% vocab ${(cmp.vocabularyCoverage * 100).toFixed(0)}% | partes ${cmp.ourPartLabels.join('')}`);
  for (const s of cmp.sections) {
    console.log(`   ${s.referenceLabel.padEnd(16)} ${s.referenceKeys.join(' ').padEnd(40)} → ${s.ourLabel ?? '—'} seq ${(s.sequenceSimilarity * 100).toFixed(0)}% vocab ${(s.vocabularyCoverage * 100).toFixed(0)}%${s.missingKeys.length ? ' faltou ' + s.missingKeys.join(' ') : ''}`);
  }
}

const [mode, ...args] = process.argv.slice(2);
if (mode === 'fetch') {
  const tt = await fetchTheorytab(args[0], args[1]);
  console.log(tt.text);
} else if (mode === 'sync') {
  const r = await sync(args[0], args[1], args[2]);
  report(args[0], args[2], r);
} else if (mode === 'batch') {
  const rows = readFileSync(args[0], 'utf8').split(/\r?\n/).filter((l) => l.trim() && !l.startsWith('#'));
  const summary = [];
  for (const row of rows) {
    const [trackId, artist, song] = row.split('\t').map((x) => x.trim());
    try {
      const r = await sync(trackId, artist, song);
      report(trackId, song, r);
      summary.push({ trackId, song, ok: true, tonic: r.cmp.tonicMatches, mode: r.cmp.modeMatches, seq: r.cmp.sequenceSimilarity, vocab: r.cmp.vocabularyCoverage });
    } catch (e) {
      console.log(`#${trackId} ${song} | ERRO ${e.message}`);
      summary.push({ trackId, song, ok: false });
    }
    await new Promise((r) => setTimeout(r, 1500));   // educação com o site
  }
  const ok = summary.filter((s) => s.ok);
  const avg = (f) => (ok.reduce((n, s) => n + f(s), 0) / Math.max(ok.length, 1));
  console.log(`\n${ok.length}/${summary.length} sincronizadas · tônica bate ${ok.filter((s) => s.tonic).length}/${ok.length} · modo bate ${ok.filter((s) => s.mode).length}/${ok.length} · seq média ${(avg((s) => s.seq) * 100).toFixed(0)}% · vocab média ${(avg((s) => s.vocab) * 100).toFixed(0)}%`);
}
