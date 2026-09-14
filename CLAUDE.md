# riff-lab

Responda e comente em **pt-BR**. Código, identificadores e mensagens de commit em inglês.

## Contexto

Plataforma de análise harmônica e tímbrica de música gravada. O objetivo não é
detectar BPM ou acorde de uma faixa — isso já existe pronto. O objetivo é
**acumular um corpus e comparar vocabulário harmônico entre artistas, álbuns e eras**,
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
    métricas de corpus.
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
  Gráficos: decisão pendente (ver "Decisões").
- **Extração:** container Docker de terceiro, configurado por URL em `application.yml`.
  Nunca acoplar o domínio ao formato de resposta de um extrator específico —
  interfaces de extração com um adapter por implementação.

## Layout do repositório

```
backend/    Maven, pacote raiz dev.rifflab
frontend/   Angular CLI
docker-compose.yml   Postgres local (rifflab/rifflab@localhost:5432/rifflab)
```

## Ambiente desta máquina

- `JAVA_HOME` do sistema aponta para JDK 1.8. Para Maven use o JDK 21:
  `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.10.7-hotspot"` (bash)
  ou `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"` (pwsh).
- `~/.m2/settings.xml` aponta para um Nexus corporativo inacessível fora da rede; por isso
  `backend/.mvn/maven.config` força `.mvn/settings.xml` (Maven Central). Não alterar o global.
- Angular CLI não é global: use `npx ng ...` dentro de `frontend/`.
- Docker Desktop disponível; Testcontainers usa o daemon local.
- Testes do backend: `cd backend && mvn test` (sobe Postgres via Testcontainers, ~40 s).
- Extrator: `docker compose build extractor` (~10 min na primeira vez, imagem de 4,2 GB: torch CPU
  + demucs). WAV sintético para smoke test: `docker run --rm -v "$PWD/extractor/out:/out"
  riff-extractor python -m app.testaudio /out/progression.wav`. `extractor/out/` é ignorado pelo git.
- Stack completo: `docker compose up -d` (Postgres + extractor em :8000) e `mvn spring-boot:run`
  em `backend/` (API em :8080; o worker faz polling da fila a cada 5 s).

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
- **Um run canônico por faixa** (`track.canonical_run_id`) para as queries de corpus.
- **A fila é `analysis_run`** (`status`, `attempts`, `locked_at`), poller `@Scheduled`
  com `SELECT … FOR UPDATE SKIP LOCKED`. Sem Kafka, sem Redis.
- **Grau é inteiro** (`degree_interval`, 0–11 semitons acima da tônica); o numeral
  romano é renderização.
- Séries por frame não vão para o Postgres: `analysis_run.features_path` (Parquet).

## Regras do `HarmonicNormalizer` (P1/P2/P3 decididos em 2026-09-13)

Pacote `dev.rifflab.harmony`, Java puro. Versão em `HarmonicNormalizer.VERSION`; mude a cada
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
- Só aqui se conhece o JSON do extrator: `extraction/riffextractor/*`. O domínio vê
  `ExtractionResult`; rótulos Harte são traduzidos por `HarteLabel`.
- **Power chord não é inferível pelo chroma** (medido em 2026-09-14, três faixas reais): sob
  distorção a intermodulação de fundamental e quinta gera 2,5f — a terça maior uma oitava acima,
  no mesmo registro da pestana. Razão terça/quinta no `chroma_low` (stem de guitarra, C2–F4):
  Teen Spirit (power chords) mediana 0,66 = Valerie (tríades limpas) 0,66; só tríades distorcidas
  (Creep, 1,24) se destacam. `PowerChordDetector` fica **desligado** (`power-chord-third-ratio: 0`);
  power chords entram como o maj/min que o BTC escolheu e `AMBIGUOUS` não ocorre com este extrator.
  O `chroma_low` continua coletado para tentativas futuras (extrator com classe "5", outra evidência).
- **Tonalidade corrigível sem re-extrair**: `PUT /api/tracks/{id}/key` grava `key_segment MANUAL`
  (inclusive modos) e re-anota o run canônico; timeline e corpus preferem `MANUAL > DERIVED >
  EXTRACTOR`. A leitura com a tonalidade do extrator permanece (unicidade da anotação inclui o
  `key_segment`). Creep: madmom deu C maior com 0,31; a correção para G maior devolve I III IV iv.
- **Run canônico é escolha do dono**: `PUT /api/tracks/{id}/canonical-run` (run DONE da faixa);
  o padrão continua sendo o primeiro run concluído.
- Fixture do contract test = resposta real do container sobre `app/testaudio.py` (WAV sintético,
  Am F C G). Nunca gravar áudio com direitos autorais no repositório.

## Decisões pendentes

- **Gráficos (Onda 4):** recomendação = ECharts para heatmap/comparação; timeline como SVG
  em template Angular dirigido por signals (`d3-scale` só se necessário).

## Ondas

- [x] Onda 0 — esqueleto (entregue e aprovado em 2026-09-13)
- [x] Onda 1 — `HarmonicNormalizer`: validado contra as 30 progressões do dono
  (`ProgressionClassificationTest`); portão aprovado em 2026-09-13 com estas convenções:
  power chord em caixa alta neutra (`I5`); `AMBIGUOUS` conta como *dentro* do campo nas métricas
  de corpus; `7sus4` reduz a `sus4`; modos (mixolídio/dórico de blues, frígio) só existem se
  atribuídos — `key_segment.source = MANUAL` ou heurística futura (backlog Onda 3).
- [x] Onda 2 — extrator próprio em `extractor/` (0.2.0), adapter, fila, worker, pipeline até
  `harmonic_annotation`, endpoints de run/timeline/key/canonical-run; contract test com a resposta
  real do container. Portão aprovado em 2026-09-14 com Valerie, Smells Like Teen Spirit e Creep:
  acordes e transições batem com o ouvido do dono; power chord não é inferível (ver Extração);
  tonalidade de baixa confiança corrigida por override manual.
- [ ] Onda 3 — analítica de corpus, entregue em 2026-09-14 (portão pendente): `GET
  /api/corpus/artists/{id}/profile`, `/albums/{id}/profile`, `/compare?a&b`,
  `/artists/{id}/pedal-passages?relation=`. Perfil = eixo A por contagem e duração, fração fora do
  campo (`AMBIGUOUS` conta como dentro), distribuição de graus (12 bins, grafia neutra `♯IV/♭V`) +
  entropia de Shannon em bits, matriz de transição 12×12 (`counts`, `rowNormalized`), relações do
  eixo B, timbre médio por álbum e stem. Comparação = JS divergence (bits, base 2) das matrizes
  normalizadas globalmente + L1 de graus e do eixo A. Uma query nativa (`CorpusQueries`) sobre run
  canônico + tonalidade preferida; métricas em Java puro (`CorpusMetrics`). A diagonal da matriz
  existe e significa troca de qualidade/baixo sobre a mesma fundamental (`IV → iv` do Creep).
  (+ backlog: tonalidade `DERIVED` por perfil de fundamentais
  quando a confiança do madmom for baixa; heurística de modo por I7/IV7 recorrentes; query de
  linha de baixo sob acorde sustentado — Valerie 3:18, Kashmir; `min_segment_duration` do BTC
  vs segmentos de < 1 s)
- [ ] Onda 4 — Angular

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
