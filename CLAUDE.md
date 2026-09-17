# Orelha (Music Sense Labs)

Responda e comente em **pt-BR**. Código, identificadores e mensagens de commit em inglês.

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

## Mapa de produto

Music Sense Labs é o guarda-chuva; Orelha é o app, com módulos que são **lentes sobre o mesmo
Acervo** (uma faixa, um run, stems, anotações). Módulo = pacote Java + grupo de rotas + seção do
menu; nada de serviço ou repositório por módulo antes de um módulo ter ciclo de vida próprio
(o extrator já tem, por ser Python).

| Módulo | Pergunta que responde | Existe | Próximo |
|---|---|---|---|
| Harmony | O que acontece harmonicamente e como artistas se comparam | `harmony`, `collection`, timeline, perfil, comparação | tonalidade `DERIVED`, modo por I7/IV7, linha de baixo sob acorde |
| Stems | Que instrumento faz o quê | extrator (demucs), player multi-stem | — |
| Practice | Como tocar junto | mixer, volumes, balanço L/R, metrônomo, letra sincronizada | versão violão e voz, andamento, loop; **Orelha no bolso** (Android, ver abaixo) |
| Production | Como o som foi construído | `timbre_summary` por álbum e stem | análise de produção (estudo em andamento) |
| Guide | O que é ouvir e entender isso | — | guia cultural e nerd na entrada; referência: Music Genome Project |

**Orelha no bolso (Android, futuro — registrado em 2026-09-16).** O celular é o módulo Practice: player
multi-stem com balanço e metrônomo, acorde/compasso/letra em execução, partes; as telas analíticas ficam
na mesa. Plano em duas fases: (1) timeline responsiva + Capacitor sobre o Angular atual, plugin de áudio em
segundo plano (tela apagada, controles na tela de bloqueio), backend alcançado por Tailscale, cache local
dos stems (~20 MB/faixa em Opus); (2) só se o WebView não segurar os quatro `<audio>` sincronizados:
player nativo Kotlin como plugin do mesmo app — MediaCodec decodifica os stems e um único AudioTrack
mistura com ganho e pan por stem e o metrônomo na mesma mistura (sincronia por amostra). Descartado:
quatro ExoPlayers (não sincronizam) e Flutter/React Native (o áudio exigiria plugin nativo do mesmo
jeito). Pré-requisito em qualquer fase: token de acesso na API antes de expô-la fora da rede local.

**Hospedagem (decidido em 2026-09-16).** Cloudflare Tunnel + Access a partir do PC do dono: o extrator
não cabe em plano gratuito, o resto é leve. O backend serve o Angular compilado na mesma origem
(`spring.web.resources.static-locations` → `frontend/dist/frontend/browser`, `SpaForwardController` faz
o fallback das rotas), então o túnel aponta para `localhost:8081` só. **No ar desde 2026-09-17 em `https://orelha.app`**: domínio no Cloudflare Registrar, túnel `orelha`
(id em `%USERPROFILE%\.cloudflared\config.yml`, serviço do Windows), Zero Trust Free com a aplicação
"Orelha" e a política "usuarios" (Allow por e-mail, One-time PIN; equipe `broken-hill-eda2`). Liberar
alguém = adicionar o e-mail na política. Roteiro e limites (100 MB por upload no plano Free) em
`deploy/README.md`; pendente: backend e `docker compose` subirem com o Windows (tarefa agendada). Plano B
com o PC desligado: Oracle Cloud Always Free para banco, backend e stems, extrator em casa.

## Contexto

Plataforma de análise harmônica e tímbrica de música gravada. O objetivo não é
detectar BPM ou acorde de uma faixa — isso já existe pronto. O objetivo é
**acumular um acervo e comparar vocabulário harmônico entre artistas, álbuns e eras**,
respondendo perguntas como:

- Que percentual dos acordes do Black Sabbath está fora do campo harmônico da tonalidade?
- Qual a matriz de transição de graus do Deep Purple vs a do Judas Priest?
- Em que passagens o baixo sustenta a fundamental enquanto a harmonia se move por mediante cromática?
- Como o centroide espectral médio das guitarras de uma banda evolui entre álbuns?

O dono do projeto é desenvolvedor Java sênior (Spring, Hibernate, PostgreSQL) e baixista,
com estudo de harmonia funcional e modal: fale no nível técnico, sem simplificar.

## Arquitetura (não negociável)

Três camadas com fronteiras rígidas:

```
[1] EXTRAÇÃO (Python, fora do nosso código)
    Serviço externo já existente. Recebe áudio, devolve JSON.
    Responsável por: separação de stems, beats/downbeats, acordes com timestamp,
    tonalidade, descritores espectrais por frame, chroma por segmento, áudio→MIDI.
        ↓ HTTP, JSON
[2] NÚCLEO (Spring Boot — o coração do projeto)
    Orquestra jobs de análise, persiste, e faz TODA a interpretação musical:
    normalização para graus, classificação funcional, matrizes de transição,
    métricas do acervo.
        ↓ REST
[3] UI (Angular)
    Visualização: timeline harmônica, heatmap de transições, comparação entre artistas.
```

**Regra de fronteira:** nenhum modelo de ML, nenhuma DSP e nenhuma dependência
Python entra no Spring Boot. Nenhuma lógica de teoria musical entra no Python.
Se você se pegar querendo cruzar essa linha, pare e pergunte.

## Stack

- **Backend:** Java 21, Spring Boot 3.5, Spring Web, Spring Data JPA, PostgreSQL 16,
  Flyway, WebClient. Testes com JUnit 5 + Testcontainers. Build Maven.
- **Frontend:** Angular 21 (LTS), standalone components, signals, zoneless, sem NgRx.
  Gráficos: ECharts (heatmap, barras) + SVG em template para a timeline (ver "Frontend").
- **Extração:** container Docker de terceiro, configurado por URL em `application.yml`.
  Nunca acoplar o domínio ao formato de resposta de um extrator específico —
  interfaces de extração com um adapter por implementação.

## Layout do repositório

```
backend/    Maven, pacote raiz dev.musicsense.orelha (org: dev.musicsense)
frontend/   Angular CLI
docker-compose.yml   Postgres local (orelha/orelha@localhost:5432/orelha, projeto compose `orelha`, volume `orelha_pgdata`)
```

## Ambiente

- Requisitos: JDK 21, Node 24 (Angular CLI não é global: `npx ng ...` em `frontend/`), Docker.
- `backend/.mvn/maven.config` força `.mvn/settings.xml` (Maven Central), ignorando qualquer
  mirror do `~/.m2/settings.xml`.
- Testes do backend: `cd backend && mvn test` (sobe Postgres via Testcontainers, ~40 s).
- Extrator: `docker compose build extractor` (~10 min na primeira vez, imagem de 4,2 GB: torch CPU
  + demucs). WAV sintético para smoke test: `docker run --rm -v "$PWD/extractor/out:/out"
  orelha-extractor python -m app.testaudio /out/progression.wav`. `extractor/out/` é ignorado pelo git.
- Stack completo: `docker compose up -d` (Postgres + extractor em :8000) e `mvn spring-boot:run`
  em `backend/` (API em :8080; o worker faz polling da fila a cada 5 s).
- Particularidades da máquina do dono (portas ocupadas, JDK do sistema, caminhos) ficam em
  `CLAUDE.local.md`, não versionado.

## Regras de trabalho

- **Trade-offs explícitos.** Toda decisão de arquitetura com mais de uma opção viável
  vem como comparação, não como fato consumado.
- **Não invente teoria musical.** Se uma regra de classificação for ambígua, pergunte.
- Ao editar código existente, preserve o estilo do trecho original.
- Não crie abstração antes do segundo caso de uso concreto.
- Nada de mock de dados na UI para "mostrar funcionando". Se o backend não devolve,
  a tela mostra vazio.
- Commits pequenos e descritivos, um por unidade lógica. Sem push automático.
- Trabalhe uma onda por vez. Ao fim de cada onda, pare, mostre o resultado e espere
  aprovação antes de seguir. Não adiante trabalho da onda seguinte.

## Modelo de dados (decisões já tomadas)

Revisado em relação ao esboço original; ver `backend/src/main/resources/db/migration/V1__schema.sql`.

- **Bruto ≠ derivado.** `chord_segment` guarda só o que o extrator devolveu.
  Tudo que o `HarmonicNormalizer` deriva vai para `harmonic_annotation`, com
  `normalizer_version` — re-derivar nunca exige re-extrair.
- **Tonalidade é por segmento** (`key_segment`), não por faixa; tonalidade global =
  um segmento cobrindo a faixa. `mode` é enum aberto (maior, menor e modos eclesiásticos).
- **Vocabulário de `quality` é nosso** (enum `ChordQuality`); o adapter traduz o dialeto
  do extrator. Inclui `NO_CHORD`, `UNKNOWN` e `POWER`.
- **Acorde de potência é decidido em Java**, a partir do `chroma[12]` do segmento
  (teste de ausência de terça). NUNCA inferir a terça pela tonalidade.
- **Baixo tem duas fontes:** `bass_pc` (rótulo do extrator, nullable) e
  `effective_bass_pc` (derivado de `bass_note`, o stem MIDI). O segundo é o confiável.
- **Beats têm tabela própria** (`beat`), com `bar_no` e `is_downbeat`.
- **Um run canônico por faixa** (`track.canonical_run_id`) para as queries do acervo.
- **A fila é `analysis_run`** (`status`, `attempts`, `locked_at`), poller `@Scheduled`
  com `SELECT … FOR UPDATE SKIP LOCKED`. Sem Kafka, sem Redis.
- **Grau é inteiro** (`degree_interval`, 0–11 semitons acima da tônica); o numeral
  romano é renderização.
- Séries por frame não vão para o Postgres: `analysis_run.features_path` (Parquet).

## Regras do `HarmonicNormalizer` (P1/P2/P3 decididos em 2026-09-13)

Pacote `dev.musicsense.orelha.harmony`, Java puro. Versão em `HarmonicNormalizer.VERSION`; mude a cada
alteração de regra (vai para `harmonic_annotation.normalizer_version`).

- **Dois eixos (P1).** Eixo A `KeyRelation` (acorde × tonalidade), precedência:
  `NONE` (sem fundamental) → `AMBIGUOUS` (power chord cuja díade cabe na escala) → `DIATONIC`
  (todas as notas, inclusive 7ª, na escala) → `BORROWED` (todas na escala paralela: maior ↔ menor
  natural; cobre os "IV dórico" e "♭VII mixolídio") → `SECONDARY_DOMINANT` (DOM7 fora do campo é
  dominante pela qualidade, resolva ou não — `E7 → F` em Dó é V/vi; tríade MAJ só com o próximo
  acorde uma 5ª abaixo) → `CHROMATIC` (inclui ♭II frígio, III/VI maiores, ♭V blue note).
  Eixo B `Transition` (acorde × anterior, sobre tríades reduzidas; guarda `rootInterval` e
  `commonTones`): `PARALLEL`, `RELATIVE`, `LEITTONWECHSEL`, `HEXATONIC_POLE` (maior r ↔ menor r+8),
  `CHROMATIC_MEDIANT` (terça, 1 comum), `DOUBLY_CHROMATIC_MEDIANT` (0), `DIATONIC_MEDIANT`,
  `MEDIANT` (terça com sus/power), `FIFTH_DOWN` (G→C), `FIFTH_UP`, `TRITONE`, `SEMITONE`,
  `WHOLE_TONE`, `SAME_ROOT`, `SAME`.
- **Referência tonal (P2).** A tonalidade do `key_segment` vigente. Menores tonais (`MINOR`,
  `DORIAN`) = escala natural + V, V7, vii°, vii°7 da harmônica como diatônicos; `AEOLIAN` e
  `PHRYGIAN` são estritos. `PHRYGIAN_DOMINANT` (tônica maior + ♭II) existe para flamenco/metal.
  Blues: `MIXOLYDIAN` (I7 diatônico) ou `DORIAN` (IV7 diatônico), conforme o caso.
  Numerais **sempre relativos à escala maior da tônica** (♭III, ♭VI, ♭VII também em menor);
  trítono = `♯IV` em modos de terça maior, `♭V` nos de terça menor; caixa pela tríade: `ii`,
  `vii°`, `iiø7`, `III+`, `IVsus4`; power chord neutro em caixa alta: `I5`, `♭VI5`.
- **Unidade (P3).** Segmentos idênticos consecutivos são fundidos antes da normalização; matriz de
  transição sem diagonal; seções repetidas contam cada vez (música como ouvida); distribuições
  por contagem de segmento e por duração — a Onda 3 expõe as duas.
- **Baixo.** `inverted = bass ≠ root`; `BassRole` ∈ {ROOT, THIRD, FIFTH, SEVENTH, SUSPENDED,
  NON_CHORD_TONE, UNKNOWN}. Pedal sob harmonia móvel é query sobre a sequência de
  `effective_bass_pc` (Onda 3).

## Extração (decidido em 2026-09-13)

- O backend Python do ChordMiniApp foi descartado no spike: BTC desligado por constante, import
  inexistente, checkpoint não publicado, Chord-CNN-LSTM sem pesos, sem tonalidade, > 6 GB.
- **`extractor/` é nosso** (FastAPI, Python 3.10, CPU): executa o `src/evaluation/test.py` do
  ChordMini (MIT, BTC 170 classes) e lê o `.lab`; madmom para beats/downbeats e tonalidade
  (24 maior/menor); demucs `htdemucs` para stems; basic-pitch no stem de baixo; librosa para
  chroma por segmento e descritores por stem (agregados no JSON, séries em Parquet); pyloudnorm.
  Contrato em `extractor/README.md`. É cola: zero teoria musical no Python.
- `POST /analyze` é síncrono (minutos); a assincronia é a fila do Spring (`analysis_run` +
  `AnalysisWorker`). Cliente Java com `RestClient` (bloqueante por desenho; WebClient traria
  reactor sem ganho).
- Só aqui se conhece o JSON do extrator: `extraction/orelhaextractor/*`. O domínio vê
  `ExtractionResult`; rótulos Harte são traduzidos por `HarteLabel`.
- **Power chord não é inferível pelo chroma** (medido em 2026-09-14, três faixas reais): sob
  distorção a intermodulação de fundamental e quinta gera 2,5f — a terça maior uma oitava acima,
  no mesmo registro da pestana. Razão terça/quinta no `chroma_low` (stem de guitarra, C2–F4):
  Teen Spirit (power chords) mediana 0,66 = Valerie (tríades limpas) 0,66; só tríades distorcidas
  (Creep, 1,24) se destacam. `PowerChordDetector` fica **desligado** (`power-chord-third-ratio: 0`);
  power chords entram como o maj/min que o BTC escolheu e `AMBIGUOUS` não ocorre com este extrator.
  O `chroma_low` continua coletado para tentativas futuras (extrator com classe "5", outra evidência).
- **Tonalidade corrigível sem re-extrair**: `PUT /api/tracks/{id}/key` grava `key_segment MANUAL`
  (inclusive modos) e re-anota o run canônico; timeline e acervo preferem `MANUAL > DERIVED >
  EXTRACTOR`. A leitura com a tonalidade do extrator permanece (unicidade da anotação inclui o
  `key_segment`). Creep: madmom deu C maior com 0,31; a correção para G maior devolve I III IV iv.
- **Run canônico é escolha do dono**: `PUT /api/tracks/{id}/canonical-run` (run DONE da faixa);
  o padrão continua sendo o primeiro run concluído.
- **Stems persistidos (0.4.0, Opus)**: o demucs grava WAV no diretório de trabalho — é o que
  basic-pitch, `chroma_low` e timbre leem, sem perda — e o que fica em `/data/stems/<sha>/` é a
  versão codificada por ffmpeg no formato `STEM_FORMAT` (compose: `opus` = Ogg/Opus 128 kbps,
  ~11× menor que WAV; `aac`, `flac`, `wav` também valem). `models.stems_codec` registra o codec.
  Stems antigos: `docker exec orelha-extractor python -m app.convert_stems` + `UPDATE
  analysis_run SET stems = replace(stems::text, '.wav"', '.ogg"')::jsonb`. Tipos MIME servidos:
  ogg/opus → `audio/ogg`, m4a/aac → `audio/mp4`, flac, wav, mp3. O extrator grava o upload como
  `audio.<ext>` (nome neutro) porque títulos com pontos já derrubaram o ChordMini, e inclui a saída
  do script na mensagem de erro quando não há `.lab`. O extrator devolve `stems` no JSON; o compose faz bind mount de `./data/{features,stems}` no host e
  `DataPaths` traduz `/data/...` → `orelha.data.host-root` (default `../data`). O backend serve
  `GET /api/tracks/{id}/stems` e `/stems/{name}` (Range) a partir do run canônico; runs anteriores
  a 0.3.0 não têm stems (a UI avisa e sugere re-análise). `data/` é ignorado pelo git.
- **`track.audio_path`**: relativo a `orelha.library.dir` com `/` (`25/Evil Woman.mp3`) para arquivos
  dentro da biblioteca; absoluto só para faixas cadastradas por path fora dela. `AudioLibrary` decide
  (`store`/`resolve`) e é o único lugar que conhece a raiz; `TrackResponse.audioPath` devolve o caminho
  resolvido. Decidido em 2026-09-15 depois que mover a pasta riff-lab → orelha quebrou o player em 235
  faixas (V6 converteu o que já existia). Mover a pasta ou trocar de máquina agora é só apontar
  `orelha.library.dir`.
- **Upload pela UI**: `POST /api/tracks/upload` (multipart `file`, `albumId`, `title?`, `trackNo?`)
  grava em `orelha.library.dir/<albumId>/<título>.<ext>` (sem sobrescrever) e enfileira;
  `TrackResponse` traz o último run (`latestRunId/Status/Error`) numa query só para a lista.
- **Importar pasta em dois passos**: preview → edição na UI → confirm. `POST /api/tracks/import/stage`
  (multipart `files`, nome = caminho relativo da pasta) guarda em `orelha.staging.dir/<uuid>/` e
  devolve `Preview{stagingId, items[]}`; `POST /api/tracks/import-path/preview {path, recursive}`
  faz o mesmo para uma pasta do servidor (`stagingId` null, arquivos ficam no lugar). Nada entra no
  catálogo no preview. `POST /api/tracks/import/confirm {stagingId, items[]}` cadastra os itens
  **como a UI os editou** (staging → biblioteca por `move`; staging apagado); `DELETE
  /api/tracks/import/stage/{id}` descarta. `/import` e `/import-path` são atalhos (preview +
  confirmar tudo). Na UI: tabela editável, ↔ troca artista/título, ⇊ aplica artista/álbum/ano às
  linhas selecionadas, duplicatas vêm desmarcadas. `AudioTags` (jaudiotagger) lê ID3/Vorbis/MP4/WAV: artista = album artist ou artist;
  álbum; ano (4 dígitos); título; número (`3/12` → 3). Sem tags: convenção `Artista/Álbum/01
  Título.ext`; nome `Artista - Título` separa o artista (a UI troca se a ordem for a outra);
  sufixos `(youtube)`/`[Official Video]` no fim do nome são descartados. Artista/álbum reusados por nome (case-insensitive); mesmo SHA-256 é
  pulado; número já ocupado no álbum vira null. Uma transação por faixa (`TransactionTemplate`).
- **Voz → MIDI (0.5.0, 2026-09-15)**: basic-pitch também no stem de voz (80–1100 Hz), `vocal_notes` no
  JSON, tabela `vocal_note` (V8), `GET /api/tracks/{id}/vocal-notes` do run canônico. Runs anteriores
  não têm voz; o dono escolheu re-analisar o acervo inteiro em vez de um backfill só da voz.
- **Letra por ASR (0.6.0, 2026-09-15)**: faster-whisper `small` (int8, CPU, pesos baixados no build da imagem,
  `WHISPER_MODEL`/`WHISPER_LANGUAGE` no compose) sobre o stem de voz devolve `lyrics` — trechos com
  `no_speech_prob` e palavras com tempo e confiança. Persistido bruto em `lyric_segment`/`lyric_word` (V9),
  idioma em `track_analysis`. `GET /api/tracks/{id}/lyrics` devolve trechos e palavras com o compasso em que
  começam. A classificação das notas de voz é derivada na leitura (`VocalNoteClassifier`, limiares em
  `orelha.lyrics.*`): `LEXICAL` (sob palavra com probabilidade ≥ 0,3, folga 120 ms), `NON_LEXICAL` (dentro de
  trecho devolvido, sem palavra: vocalise) ou `LIKELY_LEAK` (fora de qualquer trecho: solo ou teclado que
  o demucs deixou no stem). **Medido no Creep (2026-09-15)**: `no_speech_prob` fica em 0,78–0,86 em canto
  limpo e transcrito, então não serve de limiar (o do núcleo fica em 1,0 = desligado); o que separa é o
  próprio Whisper devolver ou não o trecho, com `no_speech_threshold=0.95` no extrator para a ponte sob
  guitarra distorcida não sumir — intro e solo continuam sem trecho. Trecho cujas palavras têm todas
  probabilidade < 0,3 é alucinação ("You" a 0,06 no WAV sintético, "Oh" a 0,01 sob guitarra) e o
  classificador o ignora. Notas de voz do Creep: 212 com texto, 210 vazamento (intro, solo 2:47–3:04 e a
  distorção dos refrões). A ponte cantada sob distorção (2:22–2:47) sai numa execução e some noutra: o
  Whisper não é determinístico ali; quando some, as notas dela viram vazamento — é o caso do botão
  "mostrar vazamento" e da edição manual (próxima onda). Sem letra no run, tudo é `LEXICAL`. É classificação, não
  descarte. Motivação: o dono viu solos de guitarra no piano roll da voz; a letra sincronizada também é
  a base para forma por texto (backlog Stephenson, itens 5/15/16). **Correção manual (V10, 2026-09-16)**: `PUT /api/tracks/{id}/lyrics` com a lista inteira de
  trechos e palavras grava `lyric_segment.source = MANUAL` (palavras sem probabilidade = o dono afirmou);
  leitura prefere MANUAL; lista vazia volta à transcrição; a re-análise herda a letra MANUAL como herda
  tonalidade e partes. Na UI, clicar numa palavra da célula LETRA pausa e abre a edição: vazio apaga,
  espaços dividem o tempo da palavra entre as novas; "voltar à transcrição" desfaz tudo. **Alinhamento**
  (`LyricAligner`): cada palavra recebe `noteStartS`/`midi` da nota de voz cujo ataque cai na folga de
  120 ms (o ASR marca a consoante, o basic-pitch a vogal); o compasso da palavra e o negrito na UI usam esse
  instante. Endpoints de letra vivem em `LyricsController`; `LyricsService` é o único lugar que decide a
  fonte preferida e classifica as notas de voz.
- **Re-análise herda overrides**: ao concluir um run novo, a tonalidade MANUAL e as partes MANUAL do run
  canônico anterior são copiadas para ele (a tonalidade re-anota). O canônico continua sendo escolha do
  dono (`PUT /canonical-run`).
- Fixture do contract test = resposta real do container sobre `app/testaudio.py` (WAV sintético,
  Am F C G). Nunca gravar áudio com direitos autorais no repositório.

## Partes da música (decidido em 2026-09-15)

- **Tabela `section`** por run: `start_s`, `end_s`, `cycle_end_s` (fim da 1ª repetição do ciclo),
  `repeats`, `label`, `source` ∈ {DERIVED, EXTRACTOR, MANUAL}; leitura prefere MANUAL > EXTRACTOR >
  DERIVED (como `key_segment`). `GET /api/tracks/{id}/sections[?runId]` devolve cada parte com a
  progressão de um ciclo anotada (cifra, grau, eixo A); `PUT` grava a edição do dono (lista vazia
  volta à derivação; `cycleEndS`/`repeats` opcionais mantêm o "×N"); `POST …/sections/derive`
  re-deriva sem tocar nas MANUAL. Como a tonalidade, a edição MANUAL vive no run: re-analisar exige
  re-aplicar (backlog: carregar overrides para o novo run canônico).
- **DERIVED = `SectionDeriver`** (Java puro, `harmony`): grade de compassos pelos downbeats; assinatura
  do compasso = acordes que o ocupam (≥ 20%), identidade fundamental + família da tríade (Cmaj7, C7,
  Csus2 e C5 contam como "C" só para o teste de repetição — não é classificação); ciclo = menor
  período (≤ 16 compassos) que se repete ≥ 2× cobrindo ≥ 4 compassos, tolerando 1 compasso diferente a
  cada 4 exceto nas bordas; um período maior só vence se cobrir 1,5× mais; sobras < 4 compassos ficam
  na parte anterior; N.C. nas pontas não vira parte. Letras A, B, C… por harmonia (mesma tolerância),
  em ordem de aparição — **nunca "verso"/"refrão"**: nome de função só vem de MANUAL ou de um modelo
  de estrutura (EXTRACTOR; candidato allin1, bloqueado pelo NATTEN em 2026-09-15).
- Medido em 2026-09-15 nas 236 faixas: 7,3 partes por faixa em média, 61% cíclicas. Let It Be e o
  riff do Teen Spirit saem limpos; solos e trechos com rótulos ruidosos do BTC viram partes longas
  sem ciclo — é onde o MANUAL entra.

## Frontend (decidido em 2026-09-14)

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
  compasso (a borda é compartilhada com a vizinha; mínimo de um compasso por parte). Backlog:
  arrastar bordas na timeline; definir ciclo/×N à mão.
- **Nomes de arquivo com `..`** ("N.I.B..mp3"): o guarda de path traversal do staging descarta
  segmentos `..`, nunca substitui a sequência dentro de um nome (bug corrigido em 2026-09-15:
  virava `N.I.B..b_mp3` e o ChordMini não reconhecia a extensão).

## Referência humana (2026-09-16)

- **TheoryTab colado à mão**: o Hooktheory não tem API para a análise por música (só Trends), então o
  dono abre a página (links "abrir no TheoryTab"/"artista" na timeline, exigem login) e cola tonalidade +
  seções em numerais no painel "Referência humana". `PUT /api/tracks/{id}/reference {text, url}` grava
  em `reference_analysis` (V11, texto bruto + seções JSON); `GET …/reference/compare` reduz cada numeral
  (`RomanNumeralParser`: acidentes, caixa, °/+, V/x; 7ª/sus/inversão ignoradas) e cada acorde nosso a
  "semitons acima da tônica:família" e mede por seção da referência a parte nossa mais parecida: similaridade
  de sequência (1 − edição/tamanho, repetições consecutivas fundidas) e cobertura de vocabulário, mais
  tônica/modo. **Medido em 24 faixas em 2026-09-16** (`docs/reference-theorytab-2026-09-16.md`; sincronização
  por `docs/theorytab-sync.mjs` sobre `docs/theorytab-tracks.tsv` — as páginas de música do TheoryTab são
  públicas, só as listas por artista pedem login): tônica bate em 23/24 (Lucy: Ré maior × Lá mixolídio),
  vocabulário médio 93 %, sequência média 43 %. Conclusão: os acordes do BTC estão bons; o gargalo é o
  `SectionDeriver` não fechar ciclos (partes longas), mais diminutos e acordes de passagem que o BTC perde.
  Os numerais do TheoryTab são **relativos à escala do modo declarado** (III em Fá menor = Lá♭); o
  `RomanNumeralParser` recebe o modo e faz essa leitura; "(no3)" reduz a maior como do nosso lado.
- **Trends do Hooktheory sob demanda**: `orelha.hooktheory.activkey` (env `ORELHA_HOOKTHEORY_ACTIVKEY`,
  token da conta do dono via `POST /v1/users/auth`; **segredos ficam num `.env` na raiz**, ignorado pelo
  git, que `backend/run.ps1` carrega antes do Maven — modelo em `.env.example`; sem token os endpoints respondem 409 e o botão fica
  desabilitado). `GET /api/reference/hooktheory/trends?cp=1,5,6` e `/songs?cp=` com cache de 1 dia
  (limite deles: 10 pedidos/10 s). Na UI, botão "no pop ↗" em cada parte cuja progressão é só de
  tríades diatônicas da escala maior (ids 1–7 do Hooktheory).

## Decisões pendentes

- **Backlog Stephenson** (`docs/stephenson-backlog.md`): Parte I = 17 conceitos pedidos (2026-09-15),
  Parte II = os demais 15 do livro (2026-09-16), Parte III = tabela gene do Pandora ↔ Stephenson ↔ métrica
  do Orelha e a proposta da **aba Genoma** (ficha por faixa ao estilo do musicólogo do Pandora, notas
  0–5 MANUAL guiadas por evidência automática DERIVED; módulo Guide), Parte IV = ecossistema Hooktheory
  (notação relativa colorida por grau, empréstimo com modo de origem, pré-refrão, API Trends como
  população de referência, TheoryTab como verdade humana para medir o extrator). Fundações F1–F3 e
  ondas A–F; 15 perguntas abertas ao dono no fim do documento. Só a letra por ASR (item 5b) está implementada.

## Ondas

- [x] Onda 0 — esqueleto (entregue e aprovado em 2026-09-13)
- [x] Onda 1 — `HarmonicNormalizer`: validado contra as 30 progressões do dono
  (`ProgressionClassificationTest`); portão aprovado em 2026-09-13 com estas convenções:
  power chord em caixa alta neutra (`I5`); `AMBIGUOUS` conta como *dentro* do campo nas métricas
  do acervo; `7sus4` reduz a `sus4`; modos (mixolídio/dórico de blues, frígio) só existem se
  atribuídos — `key_segment.source = MANUAL` ou heurística futura (backlog Onda 3).
- [x] Onda 2 — extrator próprio em `extractor/` (0.2.0), adapter, fila, worker, pipeline até
  `harmonic_annotation`, endpoints de run/timeline/key/canonical-run; contract test com a resposta
  real do container. Portão aprovado em 2026-09-14 com Valerie, Smells Like Teen Spirit e Creep:
  acordes e transições batem com o ouvido do dono; power chord não é inferível (ver Extração);
  tonalidade de baixa confiança corrigida por override manual.
- [ ] Onda 3 — analítica do acervo, entregue em 2026-09-14 (portão pendente): `GET
  /api/collection/artists/{id}/profile`, `/albums/{id}/profile`, `/compare?a&b`,
  `/artists/{id}/pedal-passages?relation=`. Perfil = eixo A por contagem e duração, fração fora do
  campo (`AMBIGUOUS` conta como dentro), distribuição de graus (12 bins, grafia neutra `♯IV/♭V`) +
  entropia de Shannon em bits, matriz de transição 12×12 (`counts`, `rowNormalized`), relações do
  eixo B, timbre médio por álbum e stem. Comparação = JS divergence (bits, base 2) das matrizes
  normalizadas globalmente + L1 de graus e do eixo A. Uma query nativa (`CollectionQueries`) sobre run
  canônico + tonalidade preferida; métricas em Java puro (`CollectionMetrics`). A diagonal da matriz
  existe e significa troca de qualidade/baixo sobre a mesma fundamental (`IV → iv` do Creep).
  (+ backlog: tonalidade `DERIVED` por perfil de fundamentais
  quando a confiança do madmom for baixa; heurística de modo por I7/IV7 recorrentes; query de
  linha de baixo sob acorde sustentado — Valerie 3:18, Kashmir; `min_segment_duration` do BTC
  vs segmentos de < 1 s)
- [x] Onda 4 — Angular, entregue e aprovada em 2026-09-14: acervo, timeline com playback
  sincronizado e lane de baixo efetivo, perfil com heatmap/barras/timbre/pedais, comparação com
  distâncias. Verificado ao vivo contra os runs reais (Creep, Nirvana × Radiohead).

## Glossário

- **pc** (pitch class): inteiro 0–11, C=0. Perde a grafia enarmônica; a grafia é derivada do grau.
- **degree_interval**: `(root_pc − tonic_pc) mod 12`. O numeral romano (`♭VI`, `iv`, `vii°`) é render.
- **FunctionClass**: classificação do acorde em relação à tonalidade (diatônico, empréstimo modal,
  dominante secundária, mediante cromático, acorde de potência, outro) — definição em revisão (P1).
- **Mediante cromático**: acordes com fundamentais a uma terça de distância compartilhando
  0 ou 1 nota. A classe mais importante do projeto (rock/metal).
- **P / L / R**: transformações neo-riemannianas (Parallel, Leittonwechsel, Relative) entre
  tríades consecutivas; **hexatonic pole** = L+P+L (0 notas comuns, ex.: C → A♭m).
- **Acorde de potência**: sem terça; modo indeterminado. Registrado como ambiguidade.
- **Run** (`analysis_run`): uma execução de extração sobre uma faixa, com proveniência
  (extrator, versão, modelos). Uma faixa pode ter vários; um é o canônico.
- **Stem**: pista separada por modelo (drums/bass/vocals/other; guitar/piano só no 6-stem).
- **Chroma**: vetor de 12 energias por classe de altura; dado bruto de DSP, base do teste de terça.
