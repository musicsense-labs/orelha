// Identifica os álbuns do acervo no MusicBrainz em lote, pelo backend (que respeita o 1 req/s deles).
// Só aplica sozinho quando o melhor candidato é confiante E é o único acima do limiar; o resto vira lista
// para o dono resolver no perfil do álbum. Sem --apply, apenas simula.
//
//   node docs/musicbrainz-sync.mjs [--apply] [--base http://localhost:8081] [--all]
//
// --all reexamina também os álbuns já identificados (por padrão eles são pulados).

const args = process.argv.slice(2);
const apply = args.includes('--apply');
const all = args.includes('--all');
const baseAt = args.indexOf('--base');   // sem --base, indexOf devolve -1 e args[0] não é a base
const base = (baseAt >= 0 ? args[baseAt + 1] : 'http://localhost:8081').replace(/\/$/, '');

// O 503 deles ("web server is currently busy") é comum em lote: tenta de novo depois de uma pausa.
const get = async (path, attempt = 1) => {
  const res = await fetch(base + path);
  if (!res.ok) {
    if (attempt < 3) {
      await new Promise((r) => setTimeout(r, 3000 * attempt));
      return get(path, attempt + 1);
    }
    throw new Error(`${res.status} ${path}: ${(await res.text()).slice(0, 160)}`);
  }
  return res.json();
};

// A marca de "confiante" (0,85) é conselho para o dono na tela; o lote é mais duro, porque grava sem
// ninguém olhar: "The Beatles (White Album)" de 2000 aparece com 95% e seria um erro aplicado em silêncio.
const AUTO = 0.98;

const albums = await get('/api/albums');
const artists = new Map((await get('/api/artists')).map((a) => [a.id, a.name]));
const todo = albums.filter((a) => all || !a.mbid);
console.log(`${todo.length} de ${albums.length} álbuns a examinar${apply ? '' : ' (simulação — use --apply para gravar)'}\n`);

const applied = [];
const doubtful = [];
const empty = [];

for (const album of todo) {
  const artist = artists.get(album.artistId) ?? '?';
  let result;
  try {
    result = await get(`/api/albums/${album.id}/musicbrainz/candidates`);
  } catch (e) {
    doubtful.push({ album, artist, reason: e.message });
    continue;
  }
  const confident = result.candidates.filter((c) => c.score >= AUTO);
  const best = result.candidates[0];

  if (!best) {
    empty.push({ album, artist, searched: result.searchedTitle });
    continue;
  }
  if (confident.length !== 1) {
    doubtful.push({ album, artist, searched: result.searchedTitle,
      reason: confident.length === 0 ? `nada claro o bastante (melhor: ${best.title} ${pct(best.score)})`
        : `${confident.length} candidatos empatados`, top: result.candidates.slice(0, 3) });
    continue;
  }

  const pick = confident[0];
  const line = `${artist} · ${album.title.slice(0, 60)}\n    → ${pick.title} (${pick.firstReleased ?? '—'}) ${pct(pick.score)}`
    + (album.year && pick.firstReleased && album.year !== pick.firstReleased ? `  [era ${album.year} → ${pick.firstReleased}]` : '');
  if (apply) {
    const res = await fetch(`${base}/api/albums/${album.id}/musicbrainz`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ mbid: pick.mbid, firstReleased: pick.firstReleased }),
    });
    if (!res.ok) {
      doubtful.push({ album, artist, reason: `PUT ${res.status}` });
      continue;
    }
  }
  applied.push(line);
  console.log(`${apply ? '✓' : '·'} ${line}`);
}

console.log(`\n${apply ? 'aplicados' : 'aplicáveis'}: ${applied.length} · a decidir: ${doubtful.length} · sem resultado: ${empty.length}`);

if (doubtful.length) {
  console.log('\nA DECIDIR (abra o perfil do álbum e escolha):');
  for (const d of doubtful) {
    console.log(`  ${d.artist} · ${d.album.title.slice(0, 70)}\n    ${d.reason}`);
    for (const c of d.top ?? []) {
      console.log(`      ${pct(c.score)} ${c.title} | ${c.primaryType ?? '—'} ${(c.secondaryTypes ?? []).join('/') || ''} ${c.firstReleased ?? '—'}`);
    }
  }
}
if (empty.length) {
  console.log('\nSEM RESULTADO NO MUSICBRAINZ:');
  for (const e of empty) console.log(`  ${e.artist} · ${e.album.title.slice(0, 70)} (buscou "${e.searched}")`);
}

function pct(score) {
  return `${Math.round(score * 100)}%`;
}
