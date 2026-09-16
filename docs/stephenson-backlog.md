# Backlog: conceitos de *What to Listen For in Rock* aplicados ao Orelha

Ken Stephenson, *What to Listen For in Rock: A Stylistic Analysis* (Yale, 2002). Páginas citadas
são as do livro (no PDF do dono, página do PDF = página do livro + 19). Registrado em 2026-09-15 a
pedido do dono; nada daqui está implementado. Cada item traz a definição do autor (parafraseada),
o que o Orelha já tem, a proposta, os trade-offs e as perguntas que precisam de resposta do dono
antes de codar (regra do projeto: não inventar teoria musical).

A tese do livro que atravessa todos os itens: o rock não é "prática comum mal feita", é outro
sistema. A tônica se afirma **no início** das unidades (não por cadência), as cadências são
**abertas** e variadas, a sucessão padrão é a **retrocessão** (V–IV–I), a forma é marcada por texto,
instrumentação e ritmo tanto quanto por harmonia. O Orelha hoje mede o vocabulário pela ótica da
prática comum (diatônico, empréstimo, dominante secundária, mediantes). Este backlog acrescenta a
ótica do próprio rock, sem substituir a atual: as duas leituras convivem como eixos.

## Fundações (pré-requisitos compartilhados)

Quase todos os itens dependem de três estruturas que ainda não existem. Vale construí-las
primeiro, cada uma como onda própria.

### F1. Grade hipermétrica (`Hypermeter`, pacote `harmony`, Java puro)

- **Livro:** hipercompasso = unidade de 4 compassos; downbeats 1 e 3 são fortes, 2 e 4 fracos
  (glossário, p. 235; cap. 1, pp. 5–9). Unidades de 2 e 8 ocorrem, mas 4 é a norma estatística.
- **Temos:** `beat` com `bar_no` e `is_downbeat`; `section` com `start_s`, `cycle_end_s`,
  `repeats` (derivada ou manual).
- **Proposta:** função pura `Hypermeter.of(downbeats, sections)` que numera cada compasso com
  `(hypermeasure, position 1–4)`, ancorando a contagem no início de cada parte (o ciclo detectado
  pelo `SectionDeriver` já é, na prática, um múltiplo de hipercompassos). Método
  `positionOf(seconds)` devolve `hyperbar`, `beat` e deslocamento em fração de beat. Não persistir:
  é derivação barata sobre `beat` + `section`.
- **Trade-off:** ancorar na parte (simples, consistente com a UI) × ancorar em mudanças de
  instrumentação (F3, mais fiel ao livro quando a parte começa com pickup). Começar pela parte;
  revisar quando F3 existir.
- **Pergunta ao dono:** quando o ciclo de uma parte tem 2 ou 6 compassos, a unidade hipermétrica
  é o ciclo ou permanece 4? (O livro admite unidades de 2; para 6 ele lê 4 + 2.)

### F2. Frases vocais (`VocalPhraseSegmenter`, `vocal_phrase`)

- **Livro:** frase = movimento melódico unificado; *melodic motion* = do primeiro ataque até a
  cadência; *melodic rest* = do ataque da última nota até o início da frase seguinte (glossário,
  pp. 236–238). A cadência melódica é o **ataque da última nota**, não o fim dela.
- **Temos:** `vocal_note` (basic-pitch no stem de voz, 0.5.0) com `start_s`, `end_s`, `midi`,
  `velocity`.
- **Proposta:** segmentar as notas em frases por silêncio: gap ≥ 1 beat (e ≥ 0,4 s) entre o fim
  de uma nota e o ataque da próxima fecha a frase; notas < 80 ms ou de velocity baixa são
  descartadas antes (ruído do basic-pitch). Persistir em `vocal_phrase(run_id, start_s,
  cadence_s, end_s, cadence_pc, note_count, segmenter_version)`, derivado e versionado como
  `harmonic_annotation`. Endpoint `GET /api/tracks/{id}/vocal-phrases`; a timeline desenha o
  colchete da frase sobre a lane de voz.
- **Trade-offs:** (a) limiar de silêncio fixo em beats × adaptativo por faixa (mediana dos gaps);
  fixo primeiro, medir em Let It Be, Valerie, Creep. (b) O stem de voz junta voz principal e
  backing vocals: uma resposta de coro dentro do *rest* vira frase curta espúria. Mitigação:
  frases com ≤ 2 notas e duração < 1 beat não contam como frase (viram "resposta", ver item 5).
- **Risco:** basic-pitch em voz com reverb/harmonias produz notas fantasmas; o limiar precisa ser
  medido, não escolhido.

### F3. Atividade por stem e por compasso (`stem_activity`, extrator 0.6.0)

- **Livro:** entradas e saídas de instrumentos delineiam seções (cap. 6, pp. 124–126); textura =
  número de partes ativas e sua relação rítmica (glossário, p. 242).
- **Temos:** `timbre` agregado por stem para a faixa inteira; série por frame (`rms`, `centroid`,
  `flatness`, `rolloff`) só no Parquet, que o Java não lê.
- **Proposta:** o extrator passa a devolver `stem_activity: [{stem, time_s, rms}]` com um valor
  por **beat** (agregação de DSP fica no Python, onde deve ficar); o núcleo persiste em
  `stem_activity(run_id, stem, beat_id, rms)` e deriva "stem ativo no compasso" por limiar
  relativo ao pico do stem (proposta: ≥ −30 dB do máximo). 4 stems × ~600 beats por faixa: cabe
  no Postgres sem problema.
- **Trade-off:** ler o Parquet no Java (DuckDB JDBC ou parquet-floor, sem re-extrair 236 faixas)
  × nova versão do extrator (contrato limpo, mas exige re-análise, ~6 h de fila). A re-análise
  completa já aconteceu uma vez hoje; a próxima pode agrupar F3 com outras mudanças do extrator.
  Recomendação: contrato novo, re-análise em lote quando houver mais de um motivo.

## Ritmo de frase (cap. 1)

### 1. Modelo 2 + 2

- **Livro (pp. 7–9):** o padrão dominante do rock: numa unidade de 4 compassos a frase vocal
  começa perto do primeiro downbeat e **cadencia no terceiro downbeat**; a razão movimento :
  repouso é 1 : 1. Variantes: *first-downbeat* (frase curta que cadencia no primeiro downbeat,
  p. 14), *1 + 1* (cadências no 2º e 4º downbeats, p. 19), *elisão* (p. 17).
- **Temos:** nada sobre frases; após F1 + F2, tudo.
- **Proposta:** para cada frase de `vocal_phrase`, `Hypermeter.positionOf(cadence_s)` dá o
  downbeat da cadência (tolerância: ± meio beat, porque a nota final é normalmente antecipada, ver
  item 7). Classificar a frase em `PhraseModel` ∈ {`TWO_PLUS_TWO`, `EXTENSION_OVERLAP`,
  `FIRST_DOWNBEAT`, `ONE_PLUS_ONE`, `OTHER`} e calcular a razão movimento : repouso. Métricas por
  faixa, parte, artista e era: distribuição dos modelos e razão média. Na timeline: marcar a
  cadência na lane de voz e o modelo no painel da parte.
- **Trade-off:** classificar frase a frase (ruidoso) × classificar a parte pelo modelo majoritário
  (estável, é o que o livro faz). Fazer as duas: a parte mostra o majoritário, o perfil agrega
  frases.
- **Pergunta ao dono:** frases instrumentais (solo de guitarra) entram no modelo? O livro fala de
  frase **vocal**; propor restringir à voz e tratar solos como "sem frase".

### 2. Modelo de extensão-sobreposição (*overlap*)

- **Livro (p. 10; glossário p. 233):** a frase começa perto do primeiro downbeat e **atrasa a
  cadência até o quinto downbeat**, isto é, o primeiro da unidade seguinte. A resolução cai no
  lugar do impulso: o fim de uma coisa é o começo da outra. Ligado à "motion oriented toward
  beginnings" e à ausência de fechamento (pp. 21–28).
- **Proposta:** caso do classificador do item 1: cadência a ± meio beat do primeiro downbeat da
  unidade seguinte **e** frase com duração ≥ 3 compassos (distingue de *first-downbeat*, que é
  frase curta). Registrar quando a resolução harmônica (I) coincide com essa cadência: é o
  "resolução no início" que o livro usa contra o conceito de meia cadência.
- **Sem pergunta pendente:** a definição é operacional.

### 3. Chamada e resposta no modelo 2 + 2

- **Livro (pp. 11–13):** o repouso de dois compassos do 2 + 2 é preenchido por uma resposta:
  *fill* instrumental, riff, backing vocals ou a mesma voz. A resposta não altera o modelo
  (a cadência continua no 3º downbeat), mas muda a textura.
- **Temos:** stems separados; notas de baixo e de voz.
- **Proposta:** para cada frase 2 + 2, olhar o intervalo de repouso: (a) `vocal_note` com ≤ 2
  notas ali → resposta vocal (backing ou lead); (b) F3 `stem_activity` de `other` ou `bass` com
  pico no repouso acima do nível médio da frase → resposta instrumental; (c) `bass_note` com
  densidade maior no repouso que no movimento → resposta do baixo (o caso do baixista). Rótulo
  `Response` ∈ {`VOCAL`, `INSTRUMENTAL`, `BASS`, `NONE`}. Métrica: fração de frases com resposta,
  por artista.
- **Trade-off:** sem separar lead de backing no stem de voz, "resposta vocal" mistura os dois.
  Aceitável como primeira medida; a separação exigiria outro modelo no extrator (fora de escopo).

### 4. Estrutura antiperiódica

- **Livro (cap. 5, pp. 111–113; cap. 3, pp. 59–60):** mesmo quando o rock usa progressões
  tradicionais (I–vi–IV–V, I–IV–V), ele as **repete indefinidamente terminando em V**, e o I que
  segue vem no início do hipercompasso seguinte, como impulso, não como resolução. Não há período
  (frase aberta + frase fechada): o V final não é meia cadência porque nunca vem a cadência
  completa. Muitas canções nunca fecham; quando fecham, o penúltimo acorde é IV, iv ou V11, não V.
- **Temos:** partes com progressão de ciclo (`SectionsResponse.Part.chords`), tonalidade
  preferida, `repeats`.
- **Proposta:** por parte, `CycleClosure`: primeiro acorde do ciclo é I? último acorde é I? Se
  começa em I e termina fora de I e repete ≥ 2 vezes → `ANTIPERIODIC`. Por faixa: "fecha alguma
  vez?" = existe algum I no **último** compasso de uma unidade hipermétrica (não no primeiro) com
  cadência vocal ali. Classificar o V→I que cruza a fronteira de ciclo como `BETWEEN_UNITS`, não
  como cadência (o item 7 depende disso). Perfil: fração de partes antiperiódicas, fração de faixas
  sem fechamento, distribuição do acorde final de ciclo.
- **Trade-off:** usar o ciclo derivado (pode estar errado em partes longas, hoje corrigidas à mão)
  × exigir ciclo manual. Usar o preferido (`MANUAL > DERIVED`), como o resto.

### 5. Refrão de uma linha (*refrain*)

- **Livro (pp. 136–137; glossário p. 239):** refrão = uma ou duas linhas de **texto** recorrentes
  ao **fim de cada verso**; *chorus* = seção separada com texto fixo de várias linhas; verso =
  seção que volta com texto diferente. As definições são textuais, não harmônicas.
- **Temos:** partes rotuladas A, B, C por assinatura harmônica; notas de voz; **nenhuma letra**.
- **Proposta em dois níveis.** (a) Sem letra: candidato a refrão = última frase vocal de cada
  ocorrência de uma parte repetida, quando a sequência de pitch classes e o ritmo (quantizado em
  semicolcheias) coincidem entre as ocorrências com distância de edição ≤ 20 %, enquanto as frases
  anteriores da parte diferem. Rotular como "linha recorrente" sem chamar de refrão. (b) Com
  letra: adicionar ASR ao extrator (Whisper, é ML e fica no Python) devolvendo
  `lyrics: [{start_s, end_s, text}]`; então refrão, chorus e verso seguem a definição literal, e
  a *title lyric* (p. 123) pode marcar a chegada do chorus.
- **Trade-offs:** (a) mede repetição melódica, que o próprio livro diz ser fraca como pista de
  forma (p. 130), e é o melhor possível sem texto. (b) Whisper CPU em 236 faixas de rock com voz
  distorcida: taxa de erro alta e mais ~1 min por faixa; mas destrava também o módulo Guide
  (letra na tela) e a delineação por texto. Recomendação: fazer (a) já como métrica de repetição;
  decidir (b) junto com F3 numa mesma versão do extrator.
- **Pergunta ao dono:** vale trazer letra para dentro do Orelha (direitos: só exibir o que o ASR
  transcreveu do áudio do próprio acervo, nunca importar de sites)?

## Tonalidade (cap. 2)

### 6. Determinação de tom via sucessão harmônica repetitiva (harmonia inicial persistente)

- **Livro (pp. 28–33; glossário p. 235 "initiating harmony"):** no rock a tônica não é o acorde
  em que se chega, é o acorde de que se **parte**: a harmonia que ocorre no primeiro compasso de
  cada hipercompasso de uma seção repetida tende a ser a tônica. Confirmações secundárias: quinta
  justa ou quarta justa proeminentes na melodia (1̂–5̂), paleta compatível. Os exemplos do capítulo
  mostram cadências contradizendo a tônica e perdendo.
- **Temos:** `key_segment` do madmom (24 maior/menor, `confidence`), override `MANUAL`,
  backlog antigo de tonalidade `DERIVED` por perfil de fundamentais.
- **Proposta:** `KeyDeriver` (Java puro) com três evidências ponderadas por duração:
  (1) **harmonia inicial**: acorde no 1º compasso de cada hipercompasso (F1), acumulado por
  fundamental; (2) **perfil de fundamentais** (backlog existente); (3) **paleta** (item 11): a
  tônica candidata cujo sistema cobre mais acordes. Modo: qualidade da tríade da harmonia inicial
  (maior/menor); se a tônica vier como power chord ou o stem de voz insistir em ♭3̂ sobre I maior
  (blues, p. 49), marcar `mode = AMBIGUOUS` em vez de escolher. Persistir como `key_segment
  DERIVED` (a preferência `MANUAL > DERIVED > EXTRACTOR` já existe). Reportar a discordância com o
  madmom na UI ("madmom: Dó maior 0,31 · inicial: Sol maior").
- **Trade-off:** pesos fixos (transparente, ajustável) × aprender pesos com as correções manuais
  já feitas (Creep e outras; poucas amostras). Fixos primeiro; toda correção manual vira caso de
  teste do `KeyDeriver`.
- **Pergunta ao dono:** quando (1) e (2) discordam, a harmonia inicial vence? O livro diz que sim;
  confirmar que é a regra do Orelha.

### 7. Determinação de tonalidade sem cadência autêntica

- **Livro (pp. 32–33, 55–56, 60):** a cadência autêntica V–I "quase nunca ocorre" no rock;
  quando V–I aparece, é entre frases (V fecha uma, I abre a seguinte), o que não é cadência. Logo
  V–I **não é evidência de tônica**; um algoritmo que pese V–I erra em canções que só têm V no fim
  de ciclo (Twist and Shout, The Lion Sleeps Tonight).
- **Proposta:** regra negativa no `KeyDeriver`: não somar peso a transições `FIFTH_DOWN`; usar a
  posição hipermétrica (item 4) para classificar cada V→I como `BETWEEN_UNITS` ou `WITHIN_PHRASE`
  e expor a contagem no perfil ("V–I dentro da frase: 2 %"). Isso também corrige a leitura do
  eixo B: `FIFTH_DOWN` hoje é descrito na UI como "dominante para tônica, o mais forte da harmonia
  tonal"; após este item, o texto deve distinguir o caso entre unidades.
- **Sem pergunta pendente.**

## Cadências (cap. 3)

### 8. Variedade de acordes em cadências no quarto tempo (*fourth-downbeat cadence*)

- **Livro (pp. 57–60):** categorias de cadência no rock: **quarto downbeat** (a linha vocal
  repousa no 4º compasso da unidade, qualquer que seja o acorde), **harmonicamente dirigida** (a
  linha repousa quando chega V ou I) e **puramente melódica**. No quarto downbeat "cadências
  abertas ocorrem em quase qualquer acorde": ii, iii, IV, VI, vi, VII, V/V, V/vi (Sister Golden
  Hair usa iii, iii, IV, I, I). Mesmo I pode ser aberto se vier cedo na seção. O acorde mais
  comum em cadência aberta é V. Cadência fechada existe, mas penúltimo acorde é IV ou V11.
  Transformação rítmica (pp. 61–63): a nota final costuma vir **antecipada** (no "e" de 4) ou
  atrasada; associar a nota ao acorde do downbeat, não ao acorde que soa sob ela.
- **Temos:** acordes com tempo; após F1 + F2, a posição de cada cadência.
- **Proposta:** `Cadence` derivada por frase: `downbeat` (1–4 ou 5), `chord` (o acorde do downbeat
  mais próximo, com a antecipação de meio beat resolvida para o downbeat), `melodicPc`
  (pc da nota final relativo à tônica: 1̂, 2̂, 3̂…), `closed` = acorde I **e** nota 1̂ **e** downbeat
  (definição de cadência aberta no glossário, p. 237, negada). Distribuições: acorde cadencial por
  grau (a "variedade" do título), pares melódico/harmônico (2̂–1̂ sobre V–I, 1̂–1̂ sobre IV–I,
  6̂–5̂…), fração de cadências fechadas, por artista e era. Timeline: ponto na lane de voz com
  "cadência: iii, 2̂, aberta".
- **Trade-off:** resolver a antecipação por regra fixa (meio beat) × por proximidade ao downbeat
  mais próximo (também captura atraso). Proximidade, com limite de ± 1 beat; fora disso a
  cadência é "fora da grade" e conta à parte.
- **Pergunta ao dono:** cadência "harmonicamente dirigida" (repouso quando chega V ou I fora do
  4º compasso) deve ser categoria própria ou basta registrar downbeat + acorde e deixar a
  categoria para a query?

## Melodia e harmonia (cap. 4)

### 9. Notas não-harmônicas estáveis

- **Livro (pp. 75–79):** no rock, a melodia e a harmonia são produzidas por instrumentos
  diferentes e **não precisam concordar**: notas da melodia fora do acorde não são dissonâncias
  a resolver, são estáveis. Frequência por grau: 1̂ e 5̂ (as mais comuns, por serem estáveis na
  tonalidade e não no acorde), ♭7̂ (rock inicial), ♭3̂ sobre acorde maior (blues), 2̂ (menos),
  4̂ e 6̂ (raras). Fontes: pedais, cordas soltas de guitarra. Consequência (p. 78): o **baixo
  carrega a identidade harmônica**, e a posição fundamental é a norma.
- **Temos:** `vocal_note`, `bass_note`, acordes com qualidade (tríade + 7ª), `BassRole` com
  `NON_CHORD_TONE`.
- **Proposta:** `MelodicTone` por nota de voz: pc, grau relativo à tônica, `chordTone` (pertence
  ao acorde vigente, considerando 7ª e sus), `stable` = duração ≥ 1 beat **ou** em downbeat
  **ou** nota de cadência (F2). Métrica central: distribuição dos graus das notas **estáveis e não
  harmônicas**, por faixa, artista e era; comparar com a tabela do livro (1̂/5̂ dominam, 4̂/6̂
  raras). Extra: ♭3̂ sobre I maior estável → evidência de blues para o item 6. Timeline: a nota de
  voz não harmônica ganha contorno (hoje só mostra o nome).
- **Trade-offs:** (a) "estável" por duração × por posição métrica × por cadência: o livro não dá
  limiar numérico; propor a união dos três e medir. (b) Ruído do basic-pitch: notas de passagem
  rápidas são exatamente as que mais erram; o filtro de estabilidade já as exclui.
- **Pergunta ao dono:** a 7ª maior sobre tríade maior (C com si na voz) é nota do acorde ou não
  harmônica? O livro trata o acorde como o que a base toca; propor: harmônica só se o acorde
  rotulado tiver a 7ª.

### 10. Acordes de "baixo errado" (*wrong-bass chords*)

- **Livro (cap. 7, pp. 178–180):** tríade maior com nota de baixo que **não pertence ao acorde**
  (termo de Gary Brooker, Procol Harum; também em Genesis e ELP). Origem na prática do pedal.
  Leitura alternativa: tomar o baixo como fundamental e reler o acorde como extensão (a sucessão
  vira graus descendentes). Comum na paleta menor-cromática com dominantes secundárias "de baixo
  errado".
- **Temos:** `effective_bass_pc` (do stem MIDI), `is_inverted`, `BassRole.NON_CHORD_TONE`.
  A classificação já existe; falta distinguir de pedal e agregar.
- **Proposta:** `BassRole.NON_CHORD_TONE` se divide em `PEDAL` (o baixo sustenta o mesmo pc por
  ≥ 2 acordes consecutivos — a query de pedal da Onda 3 já mede isso) e `WRONG_BASS` (o baixo
  muda junto com o acorde, para nota fora dele, por ≥ 1 beat). Exibir na cifra como `C/D` (a UI já
  faz acorde/baixo) e, no painel, a releitura "como D11" quando o dono quiser. Métrica: fração de
  segmentos `WRONG_BASS` por artista (esperado: alto em prog, baixo em blues-rock).
- **Trade-off:** releitura automática (baixo como fundamental) × só rotular. Só rotular; a
  releitura muda `degree_interval` e contaminaria as matrizes de transição. Guardar como
  anotação alternativa, não como substituição.
- **Pergunta ao dono:** limiar de duração do baixo (≥ 1 beat?) e se acorde menor com baixo
  errado entra na mesma classe (o livro só fala de tríades maiores).

### 11. Uso da paleta de menor cromático (e os três sistemas)

- **Livro (pp. 88–99; glossário pp. 231, 236, 237):** três sistemas harmônicos, cada um gera uma
  paleta por tonalidade: **menor natural** (i, III, iv, v, VI, VII), **menor cromático** (I ou i,
  N/♭II, III, IV, V, VI, VII, todas maiores exceto possivelmente a tônica: em Mi, E/Em, F, G, A,
  B, C, D) e **maior** (I, ii, V/V, iii, V/vi, iv, IV, v, V, vi, V/ii, VII). Canções misturam
  sistemas (VI inserido na penúltima frase) ou ficam ambíguas (só I, IV, V cabe em dois
  sistemas). Nights in White Satin = menor cromático com Napolitana.
- **Temos:** eixo A por acorde (`KeyRelation`), que diz se o acorde é diatônico/emprestado/
  cromático em relação à **escala**; não diz a que **sistema** o conjunto pertence. Um III maior
  em menor é `CHROMATIC` para nós e é padrão no menor cromático de Stephenson.
- **Proposta:** `PaletteClassifier` (Java puro) que, dado o conjunto de acordes de uma parte ou
  faixa (grau + qualidade, ponderado por duração), calcula a cobertura de cada sistema e rotula
  `HarmonicSystem` ∈ {`NATURAL_MINOR`, `CHROMATIC_MINOR`, `MAJOR`, `MIXED`, `AMBIGUOUS`}: um
  sistema cobre ≥ 90 % da duração → é o sistema; dois cobrem igual → `AMBIGUOUS`; nenhum → `MIXED`
  com os graus fora de todos listados. Métricas: distribuição de sistemas por artista e era (a
  hipótese do dono sobre Sabbath vs Priest pode ser "menor cromático vs menor natural"), e a
  fração de acordes fora do sistema como segunda medida de "fora do campo", ao lado da atual.
- **Trade-off:** rotular por faixa (simples) × por parte (o livro mostra mudança de sistema entre
  verso e ponte, p. 131). Por parte, agregando para a faixa.
- **Pergunta ao dono:** power chords (sem terça) contam para qualquer sistema em que a fundamental
  exista? Proposta: sim, como hoje (`AMBIGUOUS` conta como dentro).

## Sucessão harmônica (cap. 5)

### 12. Padrão de sucessão do rock (retrocessão) e movimento de fundamental padrão

- **Livro (pp. 100–110):** a prática comum define progressão por movimento de fundamental
  **4ª ascendente** (V–I), **2ª ascendente** (IV–V), **3ª descendente** (I–vi); tudo o mais é
  "retrocessão". O rock inverte: o padrão é **4ª descendente** (I–V, Jumping Jack Flash),
  **2ª descendente** (V–IV, ♭VII–♭VI, You Make Loving Fun) e **3ª ascendente** (IV–vi, VI–i,
  House of the Rising Sun). O blues de 12 compassos (V–IV–I nos compassos 9–11) é o modelo. O
  movimento **a partir da tônica** é ignorado na contagem (qualquer acorde pode seguir I).
  Dominantes secundárias não resolvem (pp. 116–119); IV predomina (p. 113).
- **Temos:** `Transition.rootInterval` (0–11) e `commonTones` calculados, mas **não
  persistidos**: `harmonic_annotation` guarda só `relation_from_prev`, e o eixo B não distingue
  direção nas terças (`CHROMATIC_MEDIANT` cobre C→E e C→A♭).
- **Proposta:** (1) migração: `harmonic_annotation.root_interval INTEGER` (re-anotar sem
  re-extrair, é o desenho do modelo). (2) `RootMotion` ∈ {`TRADITIONAL` (5, 2, 8, 9),
  `ROCK_STANDARD` (7, 10, 3, 4), `OTHER` (1, 11, 6), `FROM_TONIC` (excluída), `SAME` (0)}.
  (3) Perfil: "índice de retrocessão" = ROCK_STANDARD / (TRADITIONAL + ROCK_STANDARD), por
  artista, álbum, era; matriz 12×12 de intervalos de fundamental como terceira matriz ao lado da
  de graus. (4) Marcar dominantes secundárias que não resolvem (`SECONDARY_DOMINANT` seguida de
  transição ≠ `FIFTH_DOWN`) e a fração de tempo em IV.
- **Trade-off:** excluir movimento a partir da tônica (fiel ao livro, mas depende de a tônica
  estar certa) × contar tudo (robusto, menos fiel). Expor as duas contagens; a UI mostra a do
  livro por padrão.
- **Pergunta ao dono:** 3ª ascendente menor e maior (3 e 4 semitons) valem igual? O livro fala em
  "terça" por letra de nota, sem qualidade; propor tratar igual.

### 13. Movimento de raiz por terças ascendentes

- **Livro (pp. 106–108):** a terça ascendente é o terceiro pilar do padrão do rock (IV–vi, VI–i,
  I–iii, ii–IV); em Nights in White Satin o refrão vive dela; a chegada à tônica por terça
  ascendente (VI–i) é rock, por 4ª ascendente (V–I) é prática comum.
- **Proposta:** subconjunto do item 12 com métricas próprias: fração de transições por terça
  ascendente, os pares de graus mais comuns (IV→vi, ♭VI→i), e "chegada à tônica por terça
  ascendente" vs "por 4ª ascendente" vs "por 2ª descendente" (a tabela de chegadas à tônica).
  Cruzar com o eixo B: uma terça ascendente pode ser `RELATIVE` (C→Em? não: C→Em é
  `LEITTONWECHSEL`; Am→C é `RELATIVE`), `CHROMATIC_MEDIANT` ou `DIATONIC_MEDIANT`; o perfil
  passa a mostrar mediantes **com direção**.
- **Sem pergunta pendente** além da do item 12.

## Forma (cap. 6)

### 14. Delineação de forma via instrumentação

- **Livro (pp. 124–126):** entradas e saídas de instrumentos (bateria entra no verso 2, coro
  entra no chorus, guitarra some na ponte) são pistas de fronteira de seção, corroborando texto,
  ritmo e harmonia; nenhuma pista sozinha decide.
- **Temos:** `SectionDeriver` só por harmonia (ciclos repetidos). Partes longas e ruidosas
  ficam para edição manual.
- **Proposta:** com F3, `InstrumentationCue`: downbeat em que um stem cruza o limiar de atividade
  (entra ou sai). O `SectionDeriver` ganha um segundo sinal: fronteiras harmônicas que coincidem
  com uma cue (± 1 compasso) ganham confiança; cues sem fronteira harmônica dentro de uma parte
  longa (≥ 16 compassos) sugerem corte, exibido na UI como marca pontilhada "bateria entra aqui"
  que o dono aceita com um clique (vira MANUAL). Não cortar automaticamente sem harmonia
  concordando: o livro é explícito que instrumentação sozinha não decide.
- **Trade-off:** 4 stems do htdemucs (drums/bass/other/vocals) × 6 stems (guitar/piano
  separados): "guitarra entra" exige 6 stems, mais 2× tempo de demucs. Ficar em 4; "other" cobre
  guitarra + teclas.

### 15. Delineação de seção por textura e harmonia

- **Livro (pp. 126–133):** ritmo/textura: densidade rítmica da voz e da base muda entre verso e
  chorus (Show Me the Way, Wrapped Around Your Finger); *stop time* marca fronteiras. Harmonia:
  qualquer quebra no padrão repetido é fronteira; **verso começa em I; chorus e ponte começam em
  IV (preferido), V ou vi**; a diferença pode estar até no padrão de sucessão (verso no padrão
  rock, ponte no tradicional, Matter of Trust); basta o primeiro acorde para saber que a seção
  mudou.
- **Temos:** partes por ciclo harmônico; primeiro acorde de cada parte; notas de voz.
- **Proposta:** (1) `SectionRole` sugerido por harmonia: parte que começa em I → candidata a
  verso; começa em IV/V/vi → candidata a chorus ou ponte; ordem e repetição decidem (a que se
  repete com a mesma melodia de voz = chorus; a que ocorre uma vez, no meio = ponte). Mostrar como
  sugestão ("verso?") até o dono confirmar, porque a definição de verso e chorus é textual (item
  5). (2) Textura: notas de voz por compasso e atividade de stems (F3) por parte; diferença ≥ 50 %
  entre partes adjacentes reforça a fronteira. (3) Sucessão por parte: `RootMotion` majoritário
  por parte (item 12) exposto no painel da parte, para ver "verso retrocessivo, ponte
  tradicional".
- **Trade-off:** rótulos funcionais (verso/chorus/ponte) automáticos × letras neutras (A, B, C) com
  sugestão. Sugestão; a letra neutra continua sendo o dado.
- **Pergunta ao dono:** a UI deve permitir marcar o papel (verso, chorus, ponte, intro, solo,
  coda) na parte manual? É o campo que faltaria para o item 16.

### 16. Forma binária composta e simbolismo harmônico

- **Livro (pp. 141–143; cap. 7):** forma binária composta = duas partes principais; a primeira,
  do tamanho de uma canção inteira, segue uma forma comum (estrófica, binária arredondada,
  verso-chorus-ponte); a segunda é material **novo** repetido muitas vezes (Hey Jude, Layla,
  Closer to Home, Stairway). Simbolismo (análise de *Dark Side of the Moon*, cap. 7): o autor
  lê sol/lua nas letras como terça maior (tônica) vs terça abaixada; a estrutura harmônica
  carrega o sentido do texto.
- **Temos:** partes com assinatura harmônica e repetições; `SectionDeriver` rotula partes
  semelhantes com a mesma letra.
- **Proposta:** (1) `FormClassifier`: sequência de letras das partes → {`STROPHIC` (AAA…),
  `ROUNDED_BINARY` (AABA, com B único), `VERSE_CHORUS_BRIDGE` (ABABCB / ABABCAB), `COMPOUND_BINARY`
  (qualquer forma seguida de parte com letra nova, ≥ 4 repetições ou ≥ 25 % da duração, sem voltar
  ao material anterior), `OTHER`}. Só depois do item 15 dar papéis, o rótulo usa as letras neutras.
  (2) Simbolismo: não é automatizável sem letra; o que o núcleo pode oferecer é a **troca de modo
  por parte** (P entre partes, tônica maior ↔ menor, ♭3̂ na voz sobre I maior) exposta como evento
  para o módulo Guide, onde o texto do guia faz a leitura.
- **Trade-off:** classificar forma por letras (barato, depende da qualidade das partes) × por
  similaridade de melodia vocal (mais fiel a "chorus", mais ruído). Letras primeiro.

### 17. Significado e estrutura antiperiódica (pós-modernismo, pp. 25–28)

- **Livro:** a estrutura de frase tradicional espelha a ideia de progresso (o último evento
  resolve); o rock coloca os "finais" no início e recusa o objetivo: correspondência com o
  pós-modernismo e com letras de roda/ciclo (wheel, turn, roll). Não é métrica, é leitura.
- **Proposta:** conteúdo do módulo Guide, não do núcleo: ao mostrar uma parte `ANTIPERIODIC`
  (item 4) ou frases `EXTENSION_OVERLAP` (item 2), o guia explica a ideia com a faixa em questão
  como exemplo. Depende só dos itens 2 e 4.

## Ordem sugerida

| Onda | Itens | Por quê |
|---|---|---|
| A | 12, 13, 10 | Só Java + uma migração; re-anotação sem re-extrair; entrega o "índice de retrocessão" e mediantes com direção, o núcleo da comparação entre artistas |
| B | 11, 6, 7 | `PaletteClassifier` e `KeyDeriver`: fecham o backlog antigo de tonalidade `DERIVED`; item 6 precisa de F1 |
| C | F1, F2, 1, 2, 8, 4 | Grade hipermétrica e frases vocais; primeira leitura de ritmo de frase e cadências sobre dados que já existem (`vocal_note` 0.5.0) |
| D | 9, 3 | Notas não harmônicas e chamada-resposta: dependem de F2 (e 3 parcialmente de F3) |
| E | F3 (+ ASR opcional), 14, 15, 16, 5 | Nova versão do extrator, re-análise em lote; forma por instrumentação, textura e texto |
| F | 17 | Guide |

Cada onda passa pelo portão de sempre: medir em faixas do acervo que o dono conhece de ouvido
(Let It Be, Valerie, Teen Spirit, Creep, Evil Woman, War Pigs) antes de virar métrica do perfil.

## Perguntas abertas ao dono (resumo)

1. F1: unidade hipermétrica quando o ciclo tem 2 ou 6 compassos.
2. Item 1: frases instrumentais ficam fora do modelo de frase?
3. Item 5: trazer letra via ASR no extrator (só transcrição do áudio do acervo)?
4. Item 6: harmonia inicial vence o perfil de fundamentais quando discordam?
5. Item 8: cadência "harmonicamente dirigida" como categoria própria?
6. Item 9: 7ª maior na voz sobre tríade maior sem 7ª rotulada é harmônica?
7. Item 10: limiar de duração do baixo errado; tríades menores entram?
8. Item 11: power chord conta em qualquer sistema que tenha a fundamental?
9. Item 12: terças ascendentes menor e maior valem igual?
10. Item 15: campo de papel (verso, chorus, ponte…) na parte manual?
