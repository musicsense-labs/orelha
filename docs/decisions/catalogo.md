# Catálogo: biblioteca, upload e importação

Onde o áudio mora, como entra no acervo e como a importação de pasta lê tags e caminhos. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- **`track.audio_path`**: relativo a `orelha.library.dir` com `/` (`25/Evil Woman.mp3`) para arquivos
  dentro da biblioteca; absoluto só para faixas importadas de uma pasta do servidor, que ficam onde estão. (O cadastro por
  caminho, `POST /api/tracks {audioPath}`, saiu em 2026-09-30: qualquer usuário do Access registrava um arquivo
  da máquina e o baixava por `/audio`.) `AudioLibrary` decide
  (`store`/`resolve`) e é o único lugar que conhece a raiz; `TrackResponse.audioPath` devolve o caminho
  resolvido. Decidido em 2026-09-15 depois que mover a pasta riff-lab → orelha quebrou o player em 235
  faixas (V6 converteu o que já existia). Mover a pasta ou trocar de máquina agora é só apontar
  `orelha.library.dir` (`ORELHA_LIBRARY_DIR` no `.env`; a biblioteca do dono saiu de `data/audio` para outro
  disco em 2026-09-22 sem tocar no banco — copiar, apontar, conferir, só então apagar a origem).
- **Upload pela UI**: `POST /api/tracks/upload` (multipart `file`, `albumId`, `title?`, `trackNo?`)
  grava em `orelha.library.dir/<albumId>/<título>.<ext>` (sem sobrescrever) e enfileira;
  `TrackResponse` traz o último run (`latestRunId/Status/Error`) numa query só para a lista.
- **Importar pasta em dois passos**: preview → edição na UI → confirm. `POST /api/tracks/import/stage`
  (multipart `files`, nome = caminho relativo da pasta) guarda em `orelha.staging.dir/<uuid>/` e
  devolve `Preview{stagingId, items[]}`; `POST /api/tracks/import-path/preview {path, recursive}`
  faz o mesmo para uma pasta do servidor (`stagingId` null, arquivos ficam no lugar). Nada entra no
  catálogo no preview. `POST /api/tracks/import/confirm {stagingId, items[]}` cadastra os itens
  **como a UI os editou** (staging → biblioteca por `move`; staging apagado); `DELETE
  /api/tracks/import/stage/{id}` descarta. Os atalhos `/import` e `/import-path` (preview + confirmar tudo) saíram em
  2026-09-30: a UI sempre usa os dois passos. Na UI: tabela editável, ↔ troca artista/título, ⇊ aplica artista/álbum/ano às
  linhas selecionadas, duplicatas vêm desmarcadas. `AudioTags` (jaudiotagger) lê ID3/Vorbis/MP4/WAV: artista = album artist ou artist;
  álbum; ano (4 dígitos); título; número (`3/12` → 3). Sem tags: convenção `Artista/Álbum/01
  Título.ext`; nome `Artista - Título` separa o artista (a UI troca se a ordem for a outra);
  sufixos `(youtube)`/`[Official Video]` no fim do nome são descartados. Artista/álbum reusados por nome (case-insensitive); mesmo SHA-256 é
  pulado; número já ocupado no álbum vira null. Uma transação por faixa (`TransactionTemplate`).
