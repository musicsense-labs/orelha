# Voz e letra

Voz → MIDI, letra por ASR, classificação das notas de voz, correção manual, alinhamento e o .lrc. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- **Voz → MIDI (0.5.0, 2026-09-15)**: basic-pitch também no stem de voz (80–1100 Hz), `vocal_notes` no
  JSON, tabela `vocal_note` (V8), `GET /api/tracks/{id}/vocal-notes` do run canônico. Runs anteriores
  não têm voz; o dono escolheu re-analisar o acervo inteiro em vez de um backfill só da voz.
- **Letra por ASR (0.6.0, 2026-09-15)**: faster-whisper `small` (int8, CPU, pesos baixados no build da imagem,
  `WHISPER_MODEL`/`WHISPER_LANGUAGE` no compose) sobre o stem de voz devolve `lyrics` — trechos com
  `no_speech_prob` e palavras com tempo e confiança. Persistido bruto em `lyric_segment`/`lyric_word` (V9),
  idioma em `track_analysis`. `GET /api/tracks/{id}/lyrics` devolve trechos e palavras com o compasso em que
  começam. A classificação das notas de voz é derivada na leitura (`VocalNoteClassifier`, limiares em
  `orelha.lyrics.*`; desde 2026-09-30 sobre a letra fundida com o .lrc, a mesma que a tela mostra): `LEXICAL` (sob palavra com probabilidade ≥ 0,3, folga 120 ms), `NON_LEXICAL` (dentro de
  trecho devolvido, sem palavra: vocalise) ou `LIKELY_LEAK` (fora de qualquer trecho: solo ou teclado que
  o demucs deixou no stem). **Medido no Creep (2026-09-15)**: `no_speech_prob` fica em 0,78–0,86 em canto
  limpo e transcrito, então não serve de limiar (o do núcleo fica em 1,0 = desligado); o que separa é o
  próprio Whisper devolver ou não o trecho (o limiar de `no_speech_prob` saiu de vez em 2026-09-30), com `no_speech_threshold=0.95` no extrator para a ponte sob
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
- **Letra sincronizada do arquivo (.lrc, 2026-09-23, pacote `lyrics`)**: o app do dono (yt-mp3) baixa um `.lrc`
  ao lado de cada faixa — texto humano com carimbo por verso, sem fim de linha e sem tempo por palavra. Ele
  **não substitui o ASR, corrige o texto dele**: o Whisper sabe *quando* se canta (tempo por palavra e, sobretudo,
  os trechos sem canto, que separam voz de solo vazado no stem); o .lrc sabe *o quê*. `LrcFile` lê o arquivo,
  `LrcImporter` grava os versos em `lrc_line` (V14, por faixa e não por run: a re-análise não perde) quando a
  faixa entra no acervo, e `POST /api/admin/lrc-scan` varre o que já estava lá. `LyricMerger` (Java puro) casa
  verso a verso — janela do carimbo até o próximo, folga de 1,5 s, Needleman–Wunsch sobre as palavras sem
  acento/caixa/pontuação: igual mantém tempo do ASR, **diferente fica com o texto do .lrc e o tempo do ASR**,
  o que o ASR não ouviu entra interpolado entre as vizinhas, e o que o ASR ouviu mas o verso não tem **cai fora**
  (alucinação sobre instrumental). O tempo medido nunca é alterado: quando a ordem quebra, quem cede é a palavra
  interpolada. A fusão é **derivada na leitura** (`LyricsService`), então MANUAL continua vencendo tudo e dá para
  comparar com o bruto. Medido em 25 faixas (4778 palavras): 56 % o ASR já acertava, **19 % corrigidas**, 25 % ele
  nem ouviu, e 1042 alucinações descartadas. Acervo em 2026-09-23: 269 das 326 faixas com .lrc (8536 versos);
  16 arquivos vazios e 41 sem arquivo caem no ASR puro. Na timeline, o selo  na célula LETRA conta quanto mudou.
  **O .lrc viaja com o áudio na importação (2026-09-24)**: a tela de importar pasta envia cada `.lrc` no mesmo
  lote do áudio a que pertence (lote = um staging; noutro lote ele não estaria ao lado na hora de confirmar), o
  `.lrc` não vira linha da pré-visualização, e `ImportService.moveIntoLibrary` o leva para a biblioteca com o
  nome de destino (`03 Creep.lrc` → `Creep.lrc`). Antes disso a pasta entrava sem letra: a importação de 2229
  faixas de 2026-09-23 trouxe só o áudio. Recuperação: casar **SHA-256 do áudio** entre a origem (`D:\yt-mp3`) e
  a biblioteca — o nome não serve, o SHA garante a mesma edição e portanto a mesma sincronia — 1576 arquivos
  copiados e `POST /api/admin/lrc-scan`, fechando em **1845 faixas com letra, 67660 versos** num acervo de 2498.
- **Re-análise herda overrides**: ao concluir um run novo, a tonalidade MANUAL e as partes MANUAL do run
  canônico anterior são copiadas para ele (a tonalidade re-anota). O canônico continua sendo escolha do
  dono (`PUT /canonical-run`).

- **Alinhamento global (2026-09-30).** O `LyricMerger` cortava a música em janelas rígidas, uma por verso do .lrc
  (carimbo − 1,5 s até o próximo carimbo − 1,5 s). A cauda de um verso caía na janela do seguinte, e o alinhamento
  — em que trocar cinco palavras custa menos que deixá-las de fora — pareava por posição: em Let It Be, "Mother"
  ficava com o tempo de "times", e o fim de cada verso virava vazamento no piano roll assim que o classificador
  passou a ler a letra fundida. Agora um Needleman–Wunsch só roda sobre a música inteira, todas as palavras do .lrc
  contra todas as do ASR; um par só vale se a palavra ouvida cai entre o carimbo do seu verso e o do seguinte, com
  1,5 s de folga dos dois lados. As folgas se sobrepõem e quem decide a fronteira é o alinhamento — o casamento
  exato vence. **Medido nas mesmas 80 faixas com .lrc**: palavras mantidas 49,4 % → 60,6 %, inseridas 27,2 % → 17,9 %,
  palavras reais do ASR descartadas 3121 → 1582, notas de voz escondidas como vazamento 34,4 % → 30,3 %. As
  "correções" (21 %) ficam dentro do verso certo: ouvido errado de verdade ("grows" → "grow") ou pareamento por
  posição quando o ASR entendeu outra coisa ("you should have" × "now, you should've"); aí o tempo é aproximado,
  mas do verso certo.
