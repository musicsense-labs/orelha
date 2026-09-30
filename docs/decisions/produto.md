# Produto, marca e o Orelha no bolso

O cabeçalho histórico do CLAUDE.md (marca e mascote) e o plano do app Android. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

**Orelha** é o produto (o app que se abre); **Music Sense Labs** é a organização: namespace
`dev.musicsense`, GitHub, docs. Decidido em 2026-09-15. O mascote é o Orelha, inspirado na
cachorra Kali do dono: sentado de frente, a orelha do lado direito da imagem em pé, vitrola
quadrada aberta ao lado, sem caixas de som (homenagem ao Nipper da HMV/RCA, em outra pose e com
outro aparelho; nunca usar "hound", já é o SoundHound). Marca em Claude Design ("Marca Orelha"): o
canvas é a fonte; **v6 do mascote desde 2026-09-16** (menos infantil, olhos em amêndoa) aplicada em
`frontend/public/brand/*.svg` e `favicon.svg`. Paleta: papel `#f6f1e8`, tinta `#1c1a17`, selo (accent)
`#d9532b` (hover `#b8401f`), cinza quente `#8a8378`, linha `#d9d2c5`; tipografia Bricolage Grotesque
(800 títulos, 500 texto) e IBM Plex Mono (rótulos, graus, tempos). Tokens em `frontend/src/styles.scss`
(`--paper --surface --panel --ink --muted --line --accent --voice --bass`); links no accent, nunca azul do
navegador; cores de dado (eixo A) ficam em `shared/music.ts`. Nunca inverter as cores do mascote. Não reintroduzir "riff-lab" nem
"corpus" (hoje "Acervo" na UI e `collection` no código). Pasta do repositório, banco, usuário e
volume do Postgres migraram para `orelha` em 2026-09-15.

**Orelha no bolso (Android, futuro — registrado em 2026-09-16).** O celular é o módulo Practice: player
multi-stem com balanço e metrônomo, acorde/compasso/letra em execução, partes; as telas analíticas ficam
na mesa. Plano em duas fases: (1) timeline responsiva + Capacitor sobre o Angular atual, plugin de áudio em
segundo plano (tela apagada, controles na tela de bloqueio), backend alcançado por Tailscale, cache local
dos stems (~20 MB/faixa em Opus); (2) só se o WebView não segurar os quatro `<audio>` sincronizados:
player nativo Kotlin como plugin do mesmo app — MediaCodec decodifica os stems e um único AudioTrack
mistura com ganho e pan por stem e o metrônomo na mesma mistura (sincronia por amostra). Descartado:
quatro ExoPlayers (não sincronizam) e Flutter/React Native (o áudio exigiria plugin nativo do mesmo
jeito). Pré-requisito em qualquer fase: token de acesso na API antes de expô-la fora da rede local.
