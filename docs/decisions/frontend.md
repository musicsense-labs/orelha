# Frontend

Timeline, player multi-stem, metrônomo, tablatura, ciclo das quintas, upload e partes. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- **ECharts** (`echarts/core`, tree-shaken: heatmap + bar) para a matriz de transição e as
  distribuições; **timeline em SVG de template Angular** dirigida por signals, sem D3: cada segmento
  é um `<rect>` num `@for`, o playhead é um `computed` sobre `currentTime`, e `<audio>` nativo faz o
  playback (`GET /api/tracks/{id}/audio`, com `Range` para seek). Clicar num segmento faz seek.
  Três lanes: acordes (44 px), baixo (36 px: piano roll de `bass-notes`, o stem nota a nota, sobre
  um fundo por segmento que fica laranja quando o baixo da harmonia não é a fundamental) e voz (40 px,
  piano roll de `vocal-notes`, com notas `NON_LEXICAL` translúcidas e `LIKELY_LEAK` escondidas por padrão —
  botão "mostrar vazamento (N)" na legenda), ambas na tessitura p5–p95 da faixa, e uma quarta lane de
  16 px com os trechos da letra (texto que couber; clique faz seek). O painel ganha a célula LETRA: o trecho atual com a palavra cantada em negrito e o
  compasso. **Baixo da harmonia ≠ linha de
  baixo**: o primeiro é `effectiveBassPc` (classe que mais soa sob o segmento, decide `inverted`) e vai
  na cifra como `E♭/G`; a segunda é o stem transcrito e aparece no piano roll e na célula BAIXO do
  painel. No painel, baixo e voz mostram a nota em execução em negrito e, quando ela termina, a
  última nota fica leve (opacidade 0,4) até a próxima começar — para o nome não piscar. A timeline quebra em 1–4
  **linhas** (seletor na legenda, preferência em `localStorage`): cada linha cobre `duration/N`
  segundos com as mesmas lanes; segmentos e notas que cruzam a borda são recortados em pedaços,
  downbeats, eixo de tempo e playhead caem na linha do seu instante.
- `httpResource` para toda leitura; sem store, sem NgRx. Rotas: `/` (acervo), `/tracks/:id`
  (timeline), `/artists/:id` e `/albums/:id` (perfil), `/compare`. Parâmetros e `data` de rota
  viram inputs (`withComponentInputBinding`).
- Sem mock: cada tela mostra vazio ou a mensagem do backend quando não há dados.
- Dev: `npx ng serve` usa `proxy.conf.json` (→ :8080). Se o 8080 estiver ocupado, rode
  `.\backend\run.ps1 -Port 8081` e `npx ng serve --proxy-config proxy.local.json` (arquivo local,
  ignorado pelo git).
- Só apresentação em TypeScript (`shared/music.ts`: nomes de nota, cifra, cores, e os textos das
  relações — `KEY_RELATION_TEXT`/`CHORD_RELATION_TEXT` dão nome curto em pt-BR e explicação com
  exemplo para cada código dos eixos A e B: "sensível (L)", "mediante cromático", "quinta abaixo
  (V → I)"…; o código em inglês nunca aparece cru na tela, só no `title`); a teoria fica no backend.
- **Player multi-stem** (timeline): a mixagem é o `<audio>` mestre (relógio); cada stem é um
  `<audio>` escondido que segue play/pause/seek e é corrigido se derivar > 150 ms. **Mix e stems
  são mutuamente exclusivos** (ligar o mix silencia os stems; ligar um stem silencia o mix);
  stems se combinam entre si; duplo clique = solo; cada canal tem volume (`audio.volume`) e **balanço L/R**
  (2026-09-16: `shared/panner.ts` liga cada `<audio>` a um `StereoPannerNode` via `MediaElementSource` —
  o elemento continua fonte e relógio, nada é decodificado em memória; o grafo só é criado quando o
  balanço sai do centro; duplo clique no slider volta ao centro; o metrônomo tem panner no próprio
  grafo). Sem `AudioBufferSource` para os stems (decodificar 4 × 50 MB não vale a sincronia por amostra
  num uso local).
- **Metrônomo** (`shared/metronome.ts`): Web Audio, cliques sintetizados sobre os beats do run
  (`GET /api/tracks/{id}/beats`, downbeat acentuado a 1400 Hz, beat a 950 Hz), agendados 250 ms à
  frente do relógio do mestre a cada frame; `reset` em seek/pause. Independente do mix/stems: toca
  por cima do que estiver soando. A timeline desenha os downbeats como linhas de compasso.
- **Tablatura do baixo (2026-09-18, pacote `practice`)**: `TabArranger` (Java puro) escolhe corda e casa para cada
  `bass_note` por Viterbi sobre a sequência — custo = deslocamento de casas entre notas fretadas (até 4 casas é
  abertura de mão, meio custo; folga > 0,75 s alivia), 0,3 por corda trocada, 0,5 por casa acima da 12ª, e
  **corda solta preferida** (−0,5; decisão do dono em 2026-09-18; `?open=false` penaliza +1). Entrar ou sair de
  solta não move a mão (custo zero) — aproximação: depois da solta o algoritmo não lembra onde a mão estava.
  Afinação 4 cordas E A D G (28 33 38 43), 24 casas; nota fora do braço sobe/desce de oitava e sai
  `octaveShifted` (o basic-pitch erra a oitava em graves). `GET /api/tracks/{id}/bass-tab` (run canônico);
  `GET …/bass.mid` = `BassMidiExporter` (`javax.sound.midi`, zero dependência): notas quantizadas na grade de
  beats (tick = beat × 480 interpolado, semicolcheia), um andamento só (mediana dos beats), fórmula de compasso,
  programa 33 — para o MuseScore/TuxGuitar gerarem a tab com ritmo. Na timeline, a lane de baixo tem
  `notas | tab` (preferência no `localStorage`): quatro linhas (G em cima), casa no ataque quando cabe (≥ 9 px),
  risquinho quando não; célula BAIXO mostra `corda A · casa 3`. Limite honesto: a tab é tão boa quanto a
  transcrição — oitavas dobradas (G1+G2 no Creep) e notas curtas engolidas vêm do extrator, não do arranjo.
  Referência humana candidata: Songsterr (tabs de pessoas), como o TheoryTab é para a harmonia.
- **Ciclo das quintas** (`timeline/fifths.ts`, 2026-09-18): na célula ACORDE do painel, atrás do botão "círculo"
  (desligado por padrão, preferência no `localStorage`). Anel de fora = maiores por quintas (C no topo), anel de
  dentro = relativas menores na mesma posição; o acorde atual acende o setor da fundamental — fora se a tríade é
  maior/sus/power/7, dentro se menor ou diminuta (Cm acende "c" sob E♭) — com a cor do eixo A, e a tônica ganha
  contorno no anel do seu modo. Com o círculo, a coluna do acorde cresce e as outras cedem padding para a grade
  continuar cabendo nos 1200 px; o painel de detalhe é uma grade de colunas fixas (2026-09-17) justamente para
  não se mexer enquanto a música toca.
- **Upload** (acervo): formulário cria artista/álbum se preciso, envia multipart e faz polling
  de `/api/tracks` a cada 5 s enquanto houver run QUEUED/RUNNING; badges na fila/analisando…/falhou.
  Cada faixa tem **reprocessar** (`POST /api/tracks/{id}/analyze`): destacado quando falhou, `↻`
  nas demais; o run anterior é mantido e o canônico só muda por escolha do dono.
- **Partes** (`timeline/sections`): abaixo do acorde atual, uma linha por parte com nome, tempo, a
  progressão de um ciclo em cifras coloridas pelo eixo A e "×N"; clicar na cifra ou no tempo faz seek;
  a parte em execução fica destacada. Clicar no nome renomeia, "juntar" funde com a anterior; toda
  edição manda a lista inteira no PUT (vira MANUAL) e "voltar à derivação" manda lista vazia. Edição
  manual (2026-09-15): "✂ dividir aqui" corta a parte em execução no downbeat mais próximo do
  playhead; "zerar partes" vira uma parte A só, para marcar do zero; setas ◀ ▶ movem início/fim um
  compasso (a borda é compartilhada com a vizinha; mínimo de um compasso por parte). **Repetir** (2026-09-18):
  `⟳ repetir` numa parte faz a timeline voltar ao início dela ao cruzar o fim tocando (o laço de frames compara
  o instante anterior com o atual; um seek para depois do fim não volta; `ended` com loop volta e segue);
  ligar fora da parte leva ao início dela. **Velocidade** (2026-09-18): seletor 0,5–1,25× no transporte aplica
  `playbackRate` (com `preservesPitch`) no mestre e nos stems; o metrônomo divide a distância até o beat pela
  velocidade e escala o lookahead. Backlog: arrastar bordas na timeline; definir ciclo/×N à mão.
- **Nomes de arquivo com `..`** ("N.I.B..mp3"): o guarda de path traversal do staging descarta
  segmentos `..`, nunca substitui a sequência dentro de um nome (bug corrigido em 2026-09-15:
  virava `N.I.B..b_mp3` e o ChordMini não reconhecia a extensão).

- **Componentes da timeline (2026-09-30).** A timeline tinha 729 linhas de TS, 292 de template e 288 de estilo (acima
  do orçamento). Ficou com o relógio e as lanes; o `Mixer` (`timeline/mixer.ts`) decide *como* soa — mix × stems,
  volume, balanço, metrônomo, velocidade e os `<audio>` escondidos dos stems — e a timeline decide *quando*
  (play, pause, seek, repetir), avisando o mixer a cada mudança e a cada quadro; o `DetailPanel`
  (`timeline/detail.ts`) mostra o instante e cuida da correção da letra, pedindo pausa e avisando quando gravou.
  Preferências guardadas no navegador passam por `shared/prefs.ts`.
- **Rotas sob demanda (2026-09-30).** Acervo e timeline no bundle inicial; perfil, comparação (que trazem o
  ECharts) e administrador carregam quando abertos. Bundle inicial 1,00 MB → 393 kB (105 kB transferidos).
  Os gráficos chamavam `inject(DestroyRef)` dentro do `afterNextRender` (NG0203, e o listener de resize vazava):
  corrigido.
