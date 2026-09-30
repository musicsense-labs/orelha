# Orelha (Music Sense Labs)

Responda e comente em **pt-BR**. Código, identificadores e mensagens de commit em inglês.

**Orelha** é o produto (o app que se abre); **Music Sense Labs** é a organização (namespace `dev.musicsense`,
GitHub, docs). Nunca usar "hound" (é do SoundHound) nem reintroduzir "riff-lab" ou "corpus" (hoje "Acervo" na
UI e `collection` no código). Marca: canvas "Marca Orelha" no Claude Design é a fonte; mascote v6 em
`frontend/public/brand/*.svg` e `favicon.svg`, **nunca inverter as cores**. Paleta papel `#f6f1e8`, tinta
`#1c1a17`, selo `#d9532b` (hover `#b8401f`), cinza quente `#8a8378`, linha `#d9d2c5`; Bricolage Grotesque (800
títulos, 500 texto) e IBM Plex Mono (rótulos, graus, tempos). Tokens em `frontend/src/styles.scss`; links no accent,
nunca azul do navegador; cores de dado (eixo A) em `shared/music.ts`. Detalhes: `docs/decisions/produto.md`.

## O que é

Plataforma de análise harmônica e tímbrica de música gravada. O objetivo não é detectar BPM ou acorde de uma
faixa — isso já existe pronto. É **acumular um acervo e comparar vocabulário harmônico entre artistas, álbuns e
eras**: que percentual dos acordes do Black Sabbath está fora do campo, a matriz de transição de graus do Deep
Purple × Judas Priest, onde o baixo sustenta a fundamental enquanto a harmonia se move por mediante cromática,
como o centroide das guitarras evolui entre álbuns.

O dono é desenvolvedor Java sênior (Spring, Hibernate, PostgreSQL) e baixista, com estudo de harmonia funcional e
modal: fale no nível técnico, sem simplificar.

**Módulos são lentes sobre o mesmo Acervo** (faixa, run, stems, anotações). Módulo = pacote Java + grupo de rotas +
seção do menu; nada de serviço ou repositório próprio antes de ter ciclo de vida próprio (o extrator já tem).

| Módulo | Pergunta | Hoje | Próximo |
|---|---|---|---|
| Harmony | O que acontece harmonicamente, como artistas se comparam | `harmony`, `collection`, `metadata`; timeline, perfil, comparação | tonalidade `DERIVED`, modo por I7/IV7, linha de baixo sob acorde |
| Stems | Que instrumento faz o quê | extrator (demucs), player multi-stem | — |
| Practice | Como tocar junto | `practice`: tab e MIDI do baixo; mixer, balanço, metrônomo, letra, velocidade, repetir parte | violão e voz; Orelha no bolso (Android) |
| Production | Como o som foi construído | `timbre_summary` por álbum e stem | análise de produção |
| Guide | O que é ouvir e entender isso | — | guia cultural; aba Genoma |

## Arquitetura (não negociável)

```
[1] EXTRAÇÃO (Python, extractor/)  recebe áudio, devolve JSON: stems, beats, acordes, tonalidade,
                                   chroma por segmento, timbre agregado, áudio→MIDI, letra por ASR
        ↓ HTTP, JSON
[2] NÚCLEO (Spring Boot)           orquestra a fila, persiste e faz TODA a interpretação musical:
                                   graus, classificação funcional, matrizes, métricas do acervo
        ↓ REST
[3] UI (Angular)                   timeline, heatmap de transições, comparação entre artistas
```

**Fronteira:** nenhum modelo de ML, DSP ou dependência Python entra no Spring Boot; nenhuma teoria musical entra no
Python. Se for preciso cruzar essa linha, pare e pergunte. Só `extraction/orelhaextractor/*` conhece o JSON do
extrator; o domínio vê `ExtractionResult` (rótulos Harte traduzidos por `HarteLabel`). Um adapter por extrator.

## Stack, layout e ambiente

- **Backend** Java 21, Spring Boot 3.5, Spring Data JPA, PostgreSQL 16, Flyway (V15), `RestClient`; JUnit 5 +
  Testcontainers; Maven. Pacote raiz `dev.musicsense.orelha`.
- **Frontend** Angular 21, standalone, signals, zoneless, sem NgRx, `httpResource` para toda leitura. ECharts
  (tree-shaken) nos gráficos; timeline em SVG de template, sem D3. Perfil, comparação e admin carregam sob demanda.
- **Extrator** `extractor/` (FastAPI, Python 3.10, CPU), versão **0.7.0**, contrato em `extractor/README.md`.
- `backend/`, `frontend/` (`npx ng ...`, o CLI não é global), `extractor/`, `docker-compose.yml` (Postgres
  `orelha/orelha@localhost:5432/orelha`, volume `orelha_pgdata`, e o extrator em :8000), `deploy/`, `docs/`.
- Testes: `cd backend && mvn test` (Testcontainers). `backend/.mvn/maven.config` força o Maven Central.
- Extrator: `docker compose build extractor` e `docker compose up -d extractor` para recriar. Smoke test com o WAV
  sintético: `python -m app.testaudio` (ver README do extrator). Nunca gravar áudio com direitos autorais no repo.
- **Produção é a tarefa agendada "Orelha"** (`deploy/start-orelha.ps1`: Docker, compose e backend na **8081**,
  servindo o Angular compilado de `frontend/dist`). Reiniciar = `Stop-ScheduledTask Orelha` + `Start-ScheduledTask
  Orelha` (o script encerra o java que estiver na porta). Frontend novo = `npx ng build`, sem reiniciar.
- Particularidades da máquina (JDK, portas ocupadas, discos, caminhos, ids do túnel) ficam em `CLAUDE.local.md`.
  Segredos num `.env` na raiz, fora do git (modelo em `.env.example`), carregado por `backend/run.ps1`.

## Regras de trabalho

- **Trade-offs explícitos.** Decisão de arquitetura com mais de uma opção viável vem como comparação.
- **Não invente teoria musical.** Se uma regra de classificação for ambígua, pergunte.
- Preserve o estilo do trecho que edita. Nada de abstração antes do segundo caso de uso concreto.
- Nada de mock na UI: se o backend não devolve, a tela mostra vazio ou a mensagem do backend.
- Commits pequenos, um por unidade lógica. Sem push automático.
- Uma onda por vez: ao fim, pare, mostre o resultado e espere aprovação.
- Registre decisões e medições novas em `docs/decisions/<tema>.md`; aqui fica só o que vale e o ponteiro.

## Modelo de dados

- **Bruto ≠ derivado.** `chord_segment` guarda só o que o extrator devolveu; o que o `HarmonicNormalizer` deriva
  vai para `harmonic_annotation` com `normalizer_version` — re-derivar nunca exige re-extrair.
- **Tonalidade é por segmento** (`key_segment`), `mode` é enum aberto (maior, menor, modos eclesiásticos). Leitura
  prefere `MANUAL > DERIVED > EXTRACTOR`; `PUT /api/tracks/{id}/key` grava MANUAL e re-anota (ainda sem tela).
- **`quality` é vocabulário nosso** (`ChordQuality`, com `NO_CHORD`, `UNKNOWN`, `POWER`); o adapter traduz.
- **Power chord não é inferível pelo chroma** (medido: sob distorção a intermodulação imita a terça). Entra como o
  maj/min que o BTC escolheu; `POWER`/`AMBIGUOUS` ficam para um extrator com classe "5". NUNCA inferir a terça pela
  tonalidade.
- **Baixo tem duas fontes:** `bass_pc` (rótulo do extrator) e `effective_bass_pc` (derivado do stem MIDI, o
  confiável). Beats em tabela própria (`beat`, com `bar_no`, `is_downbeat`). Grau é inteiro (`degree_interval`,
  0–11); o numeral romano é renderização.
- **Um run canônico por faixa** (`track.canonical_run_id`; o primeiro DONE, trocável por `PUT …/canonical-run`).
  Re-análise mantém o run anterior e **herda os overrides MANUAL** (tonalidade, partes, letra) do canônico.
- **A fila é `analysis_run`** (`FOR UPDATE SKIP LOCKED`, sem Kafka/Redis): batimento a cada 30 s, reclamação dos
  RUNNING abandonados, **pausa em execução** e **pausa automática depois de 3 falhas seguidas**, faxina de pastas de
  stems órfãs, uma análise por vez. Detalhes: `docs/decisions/fila.md`.
- Stems persistidos em Opus por SHA do áudio (`/data/stems/<sha>/`, `DataPaths` traduz para o host). `track.audio_path`
  é relativo a `orelha.library.dir` (`AudioLibrary` é o único que conhece a raiz); absoluto só para o que foi
  importado de uma pasta do servidor.

## Regras do `HarmonicNormalizer` (P1/P2/P3, decididos em 2026-09-13)

Pacote `harmony`, Java puro. Mude `HarmonicNormalizer.VERSION` a cada alteração de regra.

- **Dois eixos (P1).** Eixo A `KeyRelation` (acorde × tonalidade), precedência: `NONE` (sem fundamental) →
  `AMBIGUOUS` (power chord cuja díade cabe na escala) → `DIATONIC` (todas as notas, inclusive 7ª, na escala) →
  `BORROWED` (todas na escala paralela: maior ↔ menor natural; cobre "IV dórico" e "♭VII mixolídio") →
  `SECONDARY_DOMINANT` (DOM7 fora do campo é dominante pela qualidade, resolva ou não; tríade MAJ só com o próximo
  uma 5ª abaixo) → `CHROMATIC` (inclui ♭II frígio, III/VI maiores, ♭V blue note). Eixo B `Transition` (acorde ×
  anterior, sobre tríades reduzidas; guarda `rootInterval` e `commonTones`): `PARALLEL`, `RELATIVE`,
  `LEITTONWECHSEL`, `HEXATONIC_POLE` (maior r ↔ menor r+8), `CHROMATIC_MEDIANT` (terça, 1 comum),
  `DOUBLY_CHROMATIC_MEDIANT` (0), `DIATONIC_MEDIANT`, `MEDIANT` (com sus/power), `FIFTH_DOWN`, `FIFTH_UP`,
  `TRITONE`, `SEMITONE`, `WHOLE_TONE`, `SAME_ROOT`, `SAME`.
- **Referência tonal (P2).** A tonalidade do `key_segment` vigente. Menores tonais (`MINOR`, `DORIAN`) = natural +
  V, V7, vii°, vii°7 da harmônica como diatônicos; `AEOLIAN` e `PHRYGIAN` estritos; `PHRYGIAN_DOMINANT` (tônica
  maior + ♭II) para flamenco/metal. Blues: `MIXOLYDIAN` (I7 diatônico) ou `DORIAN` (IV7). Numerais **sempre
  relativos à escala maior da tônica** (♭III, ♭VI, ♭VII também em menor); trítono = `♯IV` em modos de terça maior,
  `♭V` nos de terça menor; caixa pela tríade (`ii`, `vii°`, `iiø7`, `III+`, `IVsus4`); power chord neutro em caixa
  alta (`I5`, `♭VI5`).
- **Unidade (P3).** Segmentos idênticos consecutivos fundidos antes; matriz de transição sem diagonal; seções
  repetidas contam cada vez; distribuições por contagem e por duração. Nas métricas do acervo `AMBIGUOUS` conta como
  dentro do campo; `7sus4` reduz a `sus4`.
- **Baixo.** `inverted = bass ≠ root`; `BassRole` ∈ {ROOT, THIRD, FIFTH, SEVENTH, SUSPENDED, NON_CHORD_TONE, UNKNOWN}.
- **Partes (`SectionDeriver`)**: ciclos pela grade de compassos, letras A, B, C… por harmonia, **nunca
  "verso"/"refrão"** — nome de função só vem de MANUAL ou de um modelo de estrutura. `docs/decisions/partes.md`.

## Letra

Pacote `lyrics`: tudo sobre letra mora aqui. O ASR (faster-whisper sobre o stem de voz) sabe *quando* se canta; o
`.lrc` que vem ao lado do áudio sabe *o quê*. `LyricMerger` alinha a música inteira (Needleman–Wunsch com o carimbo
de cada verso como restrição de tempo) e a fusão é **derivada na leitura**; a correção MANUAL do dono vence tudo.
As notas de voz são classificadas (`LEXICAL`, `NON_LEXICAL`, `LIKELY_LEAK`) pela mesma letra que a tela mostra. O
`.lrc` viaja com o áudio na importação de pasta. Medições e histórico: `docs/decisions/letra.md`.

## Operação

- `orelha.app` por Cloudflare Tunnel + Access (Allow por e-mail, One-time PIN); liberar alguém = e-mail na
  política. Importar pasta só no PC do acervo (`RemoteImportGuard` barra `/api/tracks/import*` pelo túnel).
- **Administrador** (`orelha.admin.emails` ou acesso local; `AdminProperties.require`): excluir faixa, editar ou
  excluir artista/álbum, auditoria (`/admin`), fila (`GET/POST /api/admin/worker`, `…/worker/requeue-failed`),
  órfãos (`POST /api/admin/orphans?dryRun=`), varredura de `.lrc` (`POST /api/admin/lrc-scan`).
- Aliviar a CPU sem parar nada: pausar a fila no painel, ou `docker update --cpus=3 orelha-extractor` (recriar o
  container desfaz).
- Detalhes: `docs/decisions/hospedagem.md`, `docs/decisions/fila.md`, `deploy/README.md`.

## Onde estão os detalhes

| Tema | Arquivo |
|---|---|
| Marca, mascote, Orelha no bolso (Android) | `docs/decisions/produto.md` |
| Túnel, Access, auditoria, admin, exclusão | `docs/decisions/hospedagem.md` |
| Fila, batimento, órfãos, pausas | `docs/decisions/fila.md` |
| Extrator, tempos, stems, power chord, 0.7.0 | `docs/decisions/extracao.md` |
| Biblioteca, upload, importar pasta, tags | `docs/decisions/catalogo.md` |
| Voz, ASR, `.lrc`, classificação das notas | `docs/decisions/letra.md` |
| Partes da música | `docs/decisions/partes.md` |
| Timeline, mixer, metrônomo, tab, círculo | `docs/decisions/frontend.md` |
| MusicBrainz (era pelo release-group) | `docs/decisions/musicbrainz.md` |
| TheoryTab e Hooktheory | `docs/decisions/referencia.md` |
| Histórico das ondas | `docs/decisions/ondas.md` |
| Backlog Stephenson, Genoma, Pandora | `docs/stephenson-backlog.md`, `docs/pandora-genome-2026-09-16.md` |

## Pendências

- **Onda 3** (analítica do acervo): entregue em 2026-09-14, portão ainda pendente.
- Backlog: tonalidade `DERIVED` quando a confiança do madmom é baixa; modo por I7/IV7 recorrentes; linha de baixo
  sob acorde sustentado; tela para corrigir a tonalidade; impressão digital de áudio (AcoustID) por faixa;
  backlog Stephenson (15 perguntas abertas ao dono no fim do documento).

## Glossário

- **pc** (pitch class): inteiro 0–11, C=0. Perde a grafia enarmônica; a grafia é derivada do grau.
- **degree_interval**: `(root_pc − tonic_pc) mod 12`. O numeral romano (`♭VI`, `iv`, `vii°`) é render.
- **Mediante cromático**: fundamentais a uma terça, compartilhando 0 ou 1 nota. A classe mais importante do
  projeto (rock/metal).
- **P / L / R**: transformações neo-riemannianas (Parallel, Leittonwechsel, Relative) entre tríades consecutivas;
  **hexatonic pole** = L+P+L (0 notas comuns, ex.: C → A♭m).
- **Acorde de potência**: sem terça; modo indeterminado. Registrado como ambiguidade.
- **Run** (`analysis_run`): uma execução de extração sobre uma faixa, com proveniência; um é o canônico.
- **Stem**: pista separada (drums/bass/vocals/other). **Chroma**: 12 energias por classe de altura.
