// Extrai as "Features of This Track" (Music Genome Project) de páginas de música do Pandora arquivadas no
// Wayback Machine — o site bloqueia por região fora dos EUA, o arquivo não.
// Uso: node pandora-features.mjs index <artist-path> [...]   → lista páginas de música arquivadas (CDX), salva em pandora-index.json
//      node pandora-features.mjs features <wayback-url>       → imprime as features de uma página
//      node pandora-features.mjs match <tracks.json>          → casa títulos do acervo com o índice e extrai as features
import { readFileSync, writeFileSync, existsSync } from 'node:fs';

const UA = 'orelha-research/1.0 (music genome features for the owner\'s own collection)';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function cdx(path) {
  const url = `http://web.archive.org/cdx/search/cdx?url=${encodeURIComponent(path)}&output=json&filter=statuscode:200&collapse=urlkey&limit=3000`;
  for (let attempt = 0; attempt < 6; attempt++) {
    const res = await fetch(url, { headers: { 'User-Agent': UA } });
    const text = await res.text();
    if (text.startsWith('[')) {
      return JSON.parse(text).slice(1).map((r) => ({ ts: r[1], url: r[2] }));
    }
    await sleep(20000);
  }
  throw new Error('CDX indisponível para ' + path);
}

function slugify(t) {
  return t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\(.*?\)|\[.*?\]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

export async function features(waybackUrl) {
  const raw = waybackUrl.replace(/\/web\/(\d+)\//, '/web/$1id_/');
  const res = await fetch(raw, { headers: { 'User-Agent': UA } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const html = await res.text();
  const out = { title: null, album: null, artist: null, features: [], genres: [], similar: 0 };
  const t = /"songTitle":"([^"]*)"/.exec(html);
  const a = /"albumTitle":"([^"]*)"/.exec(html);
  const ar = /"artistName":"([^"]*)"/.exec(html);
  out.title = t?.[1] ?? null;
  out.album = a?.[1] ?? null;
  out.artist = ar?.[1] ?? null;
  const re = /"focusTraits":\[(.*?)\]/g;
  let m;
  const names = new Set();
  const genres = new Set();
  while ((m = re.exec(html))) {
    for (const f of m[1].matchAll(/\{"name":"([^"]+)","focusTraitSet":"([A-Z_]+)"/g)) {
      if (/many other similarities/i.test(f[1])) continue;
      (f[2] === 'AD_GENRE' ? genres : names).add(f[1]);
    }
  }
  out.features = [...names];
  out.genres = [...genres];
  const sim = /"similarTracks":\[(.*?)\]/.exec(html);
  out.similar = sim ? sim[1].split(',').filter(Boolean).length : 0;
  return out;
}

const [mode, ...args] = process.argv.slice(2);
if (mode === 'index') {
  const all = [];
  for (const p of args) {
    const rows = await cdx(p);
    const songs = rows.filter((r) => /\/artist\/[^/]+\/[^/]+\/[^/]+\/TR[\w]+/.test(r.url));
    console.log(p, rows.length, 'páginas,', songs.length, 'de música');
    all.push(...songs);
    await sleep(2000);
  }
  // uma entrada por página de música (sem query string), a cópia mais recente
  const byPath = new Map();
  for (const r of all) {
    const path = r.url.replace(/\?.*$/, '');
    const prev = byPath.get(path);
    if (!prev || r.ts > prev.ts) byPath.set(path, { ts: r.ts, url: path });
  }
  const index = [...byPath.values()].map((r) => {
    const m = /\/artist\/([^/]+)\/([^/]+)\/([^/]+)\/(TR\w+)/.exec(r.url);
    return { ts: r.ts, url: r.url, artist: m[1], album: m[2], song: m[3], id: m[4] };
  });
  writeFileSync('pandora-index.json', JSON.stringify(index, null, 1));
  console.log(index.length, 'páginas de música únicas → pandora-index.json');
} else if (mode === 'features') {
  console.log(JSON.stringify(await features(args[0]), null, 1));
} else if (mode === 'match') {
  const index = JSON.parse(readFileSync('pandora-index.json', 'utf8'));
  const tracks = JSON.parse(readFileSync(args[0], 'utf8'));   // [{id, title, artist}]
  const results = existsSync('pandora-features.json') ? JSON.parse(readFileSync('pandora-features.json', 'utf8')) : {};
  for (const t of tracks) {
    if (results[t.id]) continue;
    const slug = slugify(t.title);
    const artistSlug = slugify(t.artist);
    const cands = index.filter((p) => (p.artist === artistSlug || p.artist === artistSlug.replace(/^the-/, ''))
      && (p.song === slug || p.song.startsWith(slug + '-')));
    if (cands.length === 0) { console.log(`#${t.id} ${t.title}: sem página arquivada`); continue; }
    // prefere a página do álbum original (não coletânea "1", "past-masters") quando houver
    cands.sort((a, b) => (/^1-|past-masters|greatest|best-of|anthology/.test(a.album) ? 1 : 0) - (/^1-|past-masters|greatest|best-of|anthology/.test(b.album) ? 1 : 0) || b.ts.localeCompare(a.ts));
    let got = null;
    for (const c of cands.slice(0, 3)) {
      try {
        const f = await features(`https://web.archive.org/web/${c.ts}/${c.url}`);
        if (f.features.length) { got = { ...f, source: c.url, ts: c.ts }; break; }
      } catch (e) {
        console.log(`   ${c.url}: ${e.message}`);
      }
      await sleep(3000);
    }
    if (got) {
      results[t.id] = { title: t.title, artist: t.artist, ...got };
      console.log(`#${t.id} ${t.title} [${got.album}]: ${got.features.join(' · ')}`);
    } else {
      console.log(`#${t.id} ${t.title}: página arquivada sem features`);
    }
    writeFileSync('pandora-features.json', JSON.stringify(results, null, 1));
    await sleep(3000);
  }
}
