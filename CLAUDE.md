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

## Decisões pendentes

- **P1** `FunctionClass`: rótulo único com precedência vs dois eixos (acorde×tonalidade
  e relação de transição). Afeta a Onda 1 e as colunas de `harmonic_annotation`.
- **P2** Referência tonal: tonalidade global vs segmentos; modos como referência válida;
  V maior em menor é diatônico (harmônica) ou empréstimo (natural)?
- **P3** Unidade de contagem das métricas de corpus: segmento, duração, beat ou compasso;
  fusão de segmentos idênticos consecutivos; diagonal da matriz de transição.
- **Extrator (Onda 2):** recomendação = backend do ChordMiniApp (BTC-PL + Beat-Transformer)
  para acordes/beats; audiolla depois para stems, basic-pitch, LUFS. Fallback: container
  próprio mínimo. Nenhum dos dois devolve chroma por segmento.
- **Gráficos (Onda 4):** recomendação = ECharts para heatmap/comparação; timeline como SVG
  em template Angular dirigido por signals (`d3-scale` só se necessário).

## Ondas

- [x] Onda 0 — esqueleto (entregue 2026-09-13; portão pendente: o schema faz sentido para as perguntas do Contexto?)
- [ ] Onda 1 — `HarmonicNormalizer` (Java puro; pedir a lista de progressões antes dos testes)
- [ ] Onda 2 — integração com o extrator (contract test com fixture JSON)
- [ ] Onda 3 — analítica de corpus
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
