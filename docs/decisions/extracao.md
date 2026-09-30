# Extração (o extrator Python e o contrato)

Por que o extrator é nosso, onde o tempo vai, stems persistidos, power chord, tonalidade e run canônico. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- O backend Python do ChordMiniApp foi descartado no spike: BTC desligado por constante, import
  inexistente, checkpoint não publicado, Chord-CNN-LSTM sem pesos, sem tonalidade, > 6 GB.
- **`extractor/` é nosso** (FastAPI, Python 3.10, CPU): executa o `src/evaluation/test.py` do
  ChordMini (MIT, BTC 170 classes) e lê o `.lab`; madmom para beats/downbeats e tonalidade
  (24 maior/menor); demucs `htdemucs` para stems; basic-pitch no stem de baixo; librosa para
  chroma por segmento e descritores por stem (agregados no JSON; até a 0.6.0, também as séries por frame em
  Parquet); pyloudnorm.
  Contrato em `extractor/README.md`. É cola: zero teoria musical no Python.
- `POST /analyze` é síncrono (minutos); a assincronia é a fila do Spring (`analysis_run` +
  `AnalysisWorker`). Cliente Java com `RestClient` (bloqueante por desenho; WebClient traria
  reactor sem ganho).
- **Onde o tempo vai, e por que não paralelizamos (medido em 2026-09-23)**: o `pipeline` loga a duração de cada
  etapa. Faixa de 2:33 em 115 s: **letra/Whisper 49 s (43 %)**, **stems/demucs 36 s (31 %)**, beats/madmom 12 s,
  encode opus 4 s, o resto 13 s. A CPU fica em ~30 % de média (pico de 14 das 28 threads lógicas), o que sugeria
  folga — mas o A/B com as mesmas 4 faixas e aquecimento deu **255 s em série × 361 s com 3 processos**: paralelizar
  **piorou 1,4×**. Dar mais threads ao torch é pior ainda (28 threads: 204 s por faixa contra 115 s com o padrão de
  14) — o i7-14700 tem 8 núcleos rápidos e 12 lentos, e espalhar trabalho nos E-cores custa caro. As medições variam
  bastante entre rodadas (provável limitação térmica depois de muitos lotes), então nenhum ganho de 10–20 % seria
  confiável aqui. `EXTRACTOR_WORKERS`, `TORCH_THREADS` e `orelha.worker.concurrency` existiram para essa medição e
  saíram em 2026-09-30 (sempre valiam 1); as alavancas reais para ganhar tempo são o Whisper (modelo menor, `beam_size`, ou não
  transcrever quando a letra não interessa) e uma GPU, que acelera demucs e Whisper juntos.
- Só aqui se conhece o JSON do extrator: `extraction/orelhaextractor/*`. O domínio vê
  `ExtractionResult`; rótulos Harte são traduzidos por `HarteLabel`.
- **Power chord não é inferível pelo chroma** (medido em 2026-09-14, três faixas reais): sob
  distorção a intermodulação de fundamental e quinta gera 2,5f — a terça maior uma oitava acima,
  no mesmo registro da pestana. Razão terça/quinta no `chroma_low` (stem de guitarra, C2–F4):
  Teen Spirit (power chords) mediana 0,66 = Valerie (tríades limpas) 0,66; só tríades distorcidas
  (Creep, 1,24) se destacam. `PowerChordDetector` fica **desligado** (`power-chord-third-ratio: 0`);
  power chords entram como o maj/min que o BTC escolheu e `AMBIGUOUS` não ocorre com este extrator.
  Em 2026-09-30 (extrator 0.7.0) saíram o `chroma_low`, o detector e a coluna `chord_segment.chroma_low`
  (V15): nada os lia. `POWER` e `AMBIGUOUS` continuam no vocabulário, prontos para um extrator com classe "5".
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
  A conversão dos stems antigos para Opus foi feita uma vez; o script (`convert_stems.py`) saiu em 2026-09-30. Tipos MIME servidos:
  ogg/opus → `audio/ogg`, m4a/aac → `audio/mp4`, flac, wav, mp3. O extrator grava o upload como
  `audio.<ext>` (nome neutro) porque títulos com pontos já derrubaram o ChordMini, e inclui a saída
  do script na mensagem de erro quando não há `.lab`. O extrator devolve `stems` no JSON; o compose faz bind mount de `stems` no host (até a 0.6.0, também `features`) (`ORELHA_DATA_DIR`
  no `.env`, que o compose lê sozinho; vazio = `./data`) e `DataPaths` traduz `/data/...` → `orelha.data.host-root`
  (`ORELHA_DATA_HOST_ROOT`, o mesmo caminho; default `../data`). Trocar de disco é copiar, apontar os dois,
  recriar o extrator (`docker compose up -d extractor`) e conferir uma análise nova antes de apagar a origem. O backend serve
  `GET /api/tracks/{id}/stems` e `/stems/{name}` (Range) a partir do run canônico; runs anteriores
  a 0.3.0 não têm stems (a UI avisa e sugere re-análise). `data/` é ignorado pelo git.
- Fixture do contract test = resposta real do container sobre `app/testaudio.py` (WAV sintético,
  Am F C G). Nunca gravar áudio com direitos autorais no repositório.

- **0.7.0 (2026-09-30): o que ninguém lia sai.** `chroma_low` (ver power chord acima) e `features_path`, o Parquet
  com as séries de timbre por frame (1,5 GB em 916 faixas; o agregado já ia para `timbre_summary`). Saem o
  `pyarrow`, o volume `features` e a coluna `analysis_run.features_path` (V15). A etapa de chroma caiu para ~1,4 s
  porque não carrega mais o stem de guitarra nem faz a CQT grave. Fixture do contrato regravada com a resposta
  real da 0.7.0; a imagem 0.6.0 ficou marcada como `orelha-extractor:0.6.0` para voltar, se preciso.
