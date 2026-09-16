# Backlog: conceitos de *What to Listen For in Rock* aplicados ao Orelha

Ken Stephenson, *What to Listen For in Rock: A Stylistic Analysis* (Yale, 2002). Páginas citadas
são as do livro (no PDF do dono, página do PDF = página do livro + 19). Registrado em 2026-09-15 a
pedido do dono; a Parte I são os 17 conceitos pedidos, a Parte II os demais que o livro traz, a Parte III
os genes do Pandora e a aba Genoma. Só a letra por ASR (item 5b) está implementada. Cada item traz a definição do autor (parafraseada),
o que o Orelha já tem, a proposta, os trade-offs e as perguntas que precisam de resposta do dono
antes de codar (regra do projeto: não inventar teoria musical).

A tese do livro que atravessa todos os itens: o rock não é "prática comum mal feita", é outro
sistema. A tônica se afirma **no início** das unidades (não por cadência), as cadências são
**abertas** e variadas, a sucessão padrão é a **retrocessão** (V–IV–I), a forma é marcada por texto,
instrumentação e ritmo tanto quanto por harmonia. O Orelha hoje mede o vocabulário pela ótica da
prática comum (diatônico, empréstimo, dominante secundária, mediantes). Este backlog acrescenta a
ótica do próprio rock, sem substituir a atual: as duas leituras convivem como eixos.

## Parte I — os 17 conceitos pedidos

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

### F3. Atividade por stem e por compasso (`stem_activity`, extrator 0.7.0)

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
- **Temos (revisado em 2026-09-15):** partes rotuladas A, B, C por assinatura harmônica; notas de voz; e,
  desde o extrator 0.6.0, **letra por ASR** (`lyric_segment`/`lyric_word`, faster-whisper sobre o stem
  de voz) com palavra e compasso. A decisão da pergunta 3 foi tomada: letra entra, só transcrita do
  áudio do acervo, boa para repetição e alinhamento, não para leitura.
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
3. ~~Item 5: trazer letra via ASR no extrator?~~ Decidido em 2026-09-15: sim, extrator 0.6.0.
4. Item 6: harmonia inicial vence o perfil de fundamentais quando discordam? (O livro responde em parte:
   em conflito, a **melodia** vence a harmonia inicial — item 20. Falta decidir só o empate entre
   harmonia inicial e perfil de fundamentais sem voz.)
5. Item 8: cadência "harmonicamente dirigida" como categoria própria?
6. Item 9: 7ª maior na voz sobre tríade maior sem 7ª rotulada é harmônica?
7. Item 10: limiar de duração do baixo errado; tríades menores entram?
8. Item 11: power chord conta em qualquer sistema que tenha a fundamental?
9. Item 12: terças ascendentes menor e maior valem igual?
10. Item 15: campo de papel (verso, chorus, ponte…) na parte manual? (Vocabulário proposto no item 26.)
11. Item 19: ♭3̂ recorrente sobre tônica maior muda o modo ou vira flag "blue third"?
12. Item 23: fração de 7ªs mostrada como aproximada ou só acima de um limiar de confiança do BTC?
13. Genoma: começar pelos ~40 genes ou pelos de harmonia e forma; nomes do Pandora em inglês ao lado?

## Parte II — os demais conceitos do livro (registrados em 2026-09-16)

Levantados capítulo a capítulo depois dos 17 itens pedidos. Mesma estrutura; numeração continua.

### 18. Elisão e modelo 1 + 1 (cap. 1, pp. 17–19)

- **Livro:** *elisão* = o 4º compasso de uma unidade é ao mesmo tempo o 1º da seguinte: a cadência cai no
  que parecia downbeat fraco e o ouvido reinterpreta a grade (Goodbye Yellow Brick Road, China Grove).
  *1 + 1* = duas frases de texto separadas por repouso igual, cadências no 2º e 4º downbeats.
- **Proposta:** o `Hypermeter` (F1) precisa aceitar **reancoragem**: quando o ciclo harmônico recomeça
  um compasso antes do previsto, a unidade seguinte começa ali (evidência: início de ciclo do
  `SectionDeriver` fora da grade de 4). `PhraseModel` do item 1 já prevê `ONE_PLUS_ONE`; a elisão vira
  atributo da frase (`elided = true`) quando a cadência coincide com um recomeço de ciclo.
- **Trade-off:** reancorar automaticamente (fiel, mas propaga erro de detecção de ciclo) × só marcar.
  Marcar primeiro; reancorar quando o ciclo for MANUAL.

### 19. Fonte de alturas da melodia: pentatônica, hexatônica, diatônica (cap. 2, pp. 37–42)

- **Livro:** o conjunto de alturas da melodia limita a tônica: pentatônica [02479] → tônica em [0]
  (tríade maior) ou [9] (menor); hexatônica [024579] → [0] (maior) ou [2] (menor); diatônica
  [013568T] → [1], [8] ou [T] = jônio, mixolídio ou eólio ("as melodias do rock são jônias, eólias ou
  mixolídias"). A terça blue (♭3̂ sobre tônica maior) se lê como alteração, não como outro conjunto.
- **Temos:** `vocal_note` (pc por duração); modos hoje só existem se atribuídos à mão.
- **Proposta:** `MelodicPitchSource` (Java puro): histograma de pc da voz ponderado por duração,
  ignorando notas `LIKELY_LEAK`; escolher o menor conjunto padrão que cobre ≥ 90 % da duração
  (pentatônica, hexatônica, diatônica); listar as tônicas compatíveis com o conjunto. Serve de terceira
  evidência do `KeyDeriver` (item 6) e, sobretudo, **decide o modo** (mixolídio vs jônio, eólio vs
  menor) — fecha o backlog antigo "modo por I7/IV7" com evidência melódica em vez de só harmônica.
- **Trade-off:** basic-pitch erra oitava e semitom em voz com vibrato; usar só notas ≥ 1 beat.
- **Pergunta ao dono:** ♭3̂ recorrente sobre tônica maior vira `mode = MIXOLYDIAN`/blues ou fica como
  flag "blue third" mantendo o modo maior? O livro trata como alteração (modo maior); propor a flag.

### 20. Em conflito, a melodia vence a harmonia inicial (cap. 2, pp. 43–47)

- **Livro:** Sweet Home Alabama e Werewolves of London têm a mesma sucessão (três tríades maiores,
  2ª descendente e 4ª descendente); a primeira é I–♭VII–IV em Ré porque a melodia gira em Ré; a segunda
  é V–IV–I em Sol porque a melodia insiste em Sol. Em Ain't No Mountain High Enough a tônica é um
  acorde quase ausente, estabelecido por uma 4ª justa da voz. Regra do autor: **na dúvida, a melodia
  decide**.
- **Proposta:** responde a pergunta 4 da Parte I: no `KeyDeriver`, a evidência melódica (pc de
  resolução das frases, item 8, e centro do histograma, item 19) pesa mais que a harmonia inicial
  quando discordam; a harmonia inicial vale como padrão na ausência de voz (intro, instrumental).
  Expor a discordância na UI como fez com o madmom.

### 21. Múltiplas tonalidades por seção e relações entre elas (cap. 2, pp. 47–49)

- **Livro:** verso e refrão em tonalidades diferentes é comum; as relações caem em cinco tipos:
  **relativas**, **paralelas**, **de quarta**, **terça cromática** e **segunda cromática** (as duas
  últimas as mais frequentes: What a Fool Believes, Ré ↔ Mi♭; Don't Worry Baby, Ré ↔ Mi♭). Algumas
  músicas oscilam entre relativas sem decidir (Face the Fire), e o acorde final não precisa ser a
  tônica (Lonely People termina no vi).
- **Temos:** `key_segment` já é por trecho; só usamos um segmento por faixa.
- **Proposta:** `KeyDeriver` por parte (item 6 + F1): um `key_segment DERIVED` por parte quando a
  tônica derivada difere da global; `SectionKeyRelation` ∈ {RELATIVE, PARALLEL, FOURTH,
  CHROMATIC_THIRD, CHROMATIC_SECOND, OTHER} entre partes adjacentes; perfil: distribuição por
  artista. Oscilação entre relativas → `AMBIGUOUS_RELATIVE` em vez de escolher. Isso muda o eixo A por
  parte (o "cromático" de um refrão em outra tonalidade some) — expor as duas leituras.
- **Trade-off:** tonalidade por parte melhora o eixo A, mas fragmenta as métricas do acervo (matriz de
  transição cruza fronteiras de tonalidade). Manter a global como referência do acervo e a por parte
  como leitura da timeline, até medir.

### 22. Ambiguidade de modo: quinta vazia e terça blue (cap. 2, pp. 49–50)

- **Livro:** o modo é dado **só pela qualidade da tríade tônica**; quando a tônica é quinta vazia
  (Smoke on the Water, I Heard It Through the Grapevine) o modo fica aberto, e a distorção ainda
  sugere terça maior (soma de frequências) enquanto a melodia usa ♭3̂. Terça blue sobre tônica maior
  não muda o modo.
- **Temos:** power chord não é detectável pelo chroma (medido); o BTC escolhe maj/min por conta
  própria; `mode` é enum fechado.
- **Proposta:** `mode = AMBIGUOUS` como valor legítimo de `key_segment` quando (a) a tônica derivada
  vem majoritariamente como power chord ou sus, ou (b) o BTC alterna maj/min sobre a mesma
  fundamental tônica; a terça da voz (item 19) desempata só se o dono quiser. Numerais em modo
  ambíguo: grafia neutra (já existe para ♯IV/♭V).

### 23. Tipos de acorde como marcador de estilo e era (cap. 4, pp. 82–88)

- **Livro:** tríades dominam em toda a era; 7ª de dominante como tônica nos primeiros 15 anos e no
  blues; maj7/min7 como tônicas a partir de 1975 (soft rock, soul, funk, disco); 7♯9 (funk, Purple
  Haze); add2/add9 nos anos 80 (Every Breath You Take); add4 quase só sobre V com 1̂ estável (Baba
  O'Riley); add6 raro; diminuto raro ("a exceção prova a regra"); aumentado só como harmonia linear
  (I → I+ → vi); m(maj7) quase só no IV maior; V11 (= IV/5̂, "Gm7/C"); quinta vazia (metal); sus4 sem
  preparação nem resolução obrigatória; sus2.
- **Temos:** `ChordQuality` por segmento (o BTC tem 170 classes: maj, min, 7, maj7, min7, sus2, sus4,
  dim, aug, 6, 9, 11, 13, hdim7, dim7…), `HarteLabel` traduz.
- **Proposta:** métrica de **vocabulário de qualidades** por artista, álbum e década (fração de duração
  por família: tríade, 7 dominante, maj7/min7, add/sus, quinta vazia, dim/aug, estendidos) — é só
  query sobre o que existe. Eventos raros viram destaque na timeline: diminuto, aumentado (verificar
  se é linear: fundamental igual à do acorde anterior e próximo acorde por grau conjunto), m(maj7).
  Idioma V11: `chord/bass` com baixo uma 5ª abaixo da fundamental de um m7 → rótulo alternativo
  "V11" no painel.
- **Pergunta ao dono:** o BTC confunde 7 e maj7 com frequência sob distorção; vale mostrar a fração de
  7ªs como "aproximada" ou só a partir de um limiar de confiança do segmento?

### 24. Predomínio da subdominante (cap. 5, p. 113)

- **Livro:** no blues de 12 compassos IV ocupa 3–4× o tempo de V; muitos trechos usam só I e IV
  (Tapestry, Sally Simpson com IV nos compassos fortes).
- **Proposta:** duas medidas baratas sobre a anotação existente: razão de duração IV : V por faixa,
  artista e era; e partes cujo conjunto de graus ⊆ {I, IV} ("vamp de I–IV"). Entra na família do
  item 12 e serve de gene no Genoma (Parte III).

### 25. Dominantes secundárias sem resolução (cap. 5, pp. 114–117)

- **Livro:** V/V raramente vai a V — vai a IV por terça ascendente (Eight Days a Week) ou a I; V/vi → IV
  é **a norma**, não a exceção (Bad Bad Leroy Brown); V/ii costuma resolver; V/IV é indistinguível de
  I quando I7 é padrão (blues), então o rótulo só faz sentido em peças triádicas; vii° secundário
  raro e também "errado".
- **Temos:** `SECONDARY_DOMINANT` no eixo A (DOM7 fora do campo, resolva ou não).
- **Proposta:** métrica **sucessor da dominante secundária**: distribuição do próximo acorde por
  grau (IV, I, ii, V…) e fração que resolve tradicionalmente, por artista. Regra do V/IV: em faixas
  com modo `MIXOLYDIAN` (item 19) I7 é diatônico, como já fazemos; sem modo atribuído, não rotular I7
  como V/IV (hoje não rotulamos — manter).

### 26. Nomes de seções: intro, link, verso, refrão, chorus, ponte, solo (cap. 6, pp. 133–138)

- **Livro:** intro = tudo antes da voz (mesmo um acorde); intro com ideia melódica costuma voltar logo
  que a voz termina uma seção ("link"/"turnaround", os músicos dizem "repete a intro"); verso = volta
  com texto novo (último verso pode repetir o primeiro); refrão = 1–2 linhas que fecham o verso ou
  abrem o chorus; chorus = seção com texto fixo, historicamente cantada pelo coro; ponte = depois do
  2º chorus, leva de volta; terceiro verso instrumental = solo sobre a harmonia do verso, **não é
  ponte**. Muitas canções começam pelo chorus.
- **Proposta:** vocabulário `SectionRole` ∈ {INTRO, LINK, VERSE, REFRAIN, CHORUS, BRIDGE, SOLO,
  CODA} para a parte MANUAL (pergunta 10 da Parte I) e para sugestões: `SOLO` = parte com a assinatura
  harmônica de um verso e sem notas de voz `LEXICAL`; `LINK` = assinatura da intro reaparecendo logo
  após a última frase vocal de uma seção; `INTRO` = antes da primeira nota de voz. Verso/chorus
  continuam dependendo de letra (item 5) ou do dono.

### 27. As quatro formas completas e suas variantes (cap. 6, pp. 139–143)

- **Livro:** estrófica (uma seção repetida, quase sempre com refrão; ou par verso–chorus repetido),
  binária arredondada (chorus–ponte dominou os anos 50; verso–ponte depois de 1968), verso–chorus–
  ponte em duas variantes (VCVCBC, VCVCBVC) e irregulares (VCVCBVBCC, VCBCVBC), binária composta.
  Ex. 6.10 dá a evolução histórica.
- **Proposta:** refina o item 16: `FormClassifier` devolve o padrão de letras como string ("VCVCBC")
  e a categoria; prior por década (chorus–ponte antes de 1965). A estrófica de blues (12 compassos
  repetidos) é o caso mais fácil: ciclo de 12 com I–IV–V.

### 28. Análise estilística × análise crítica (cap. 7, Meyer)

- **Livro:** análise de estilo revela as probabilidades do gênero; análise crítica explica o que é
  idiossincrático numa peça, e só faz sentido contra a norma. Os seis primeiros capítulos são a norma;
  o sétimo, a crítica.
- **Proposta:** é o desenho de dois níveis do Orelha: o acervo mede a norma; a timeline deveria
  mostrar o **desvio**. Métrica barata: para cada transição, cadência e tipo de acorde da faixa, a
  raridade no acervo (ou no artista, ou na década) = 1 − frequência; eventos abaixo de 2 % ganham
  destaque "raro neste artista/nesta década". Usa as matrizes que a Onda 3 já calcula.

### 29. Primeira aparição de um grau como pista de forma (cap. 7, Help Me Rhonda)

- **Livro:** em Help Me Rhonda a **primeira aparição do V** anuncia o chorus; o arranjo (linhas
  instrumentais, coro respondendo ao solista) decide não só a textura mas a composição; cadência ii–I;
  V/V → I.
- **Proposta:** `FirstAppearanceCue` no `SectionDeriver`: o instante em que um grau (ou um tipo de
  acorde) soa pela primeira vez na faixa é candidato a fronteira, com o mesmo tratamento das cues de
  instrumentação (item 14): reforça fronteira harmônica, nunca corta sozinho.

### 30. Tabela de passagens e trechos sem métrica (cap. 7, The Endless Enigma)

- **Livro:** a análise da peça do ELP começa por uma tabela — passagem, instrumentação, métrica,
  tonalidade, tempo — que inclui trechos em métrica livre, 6/8 alternando com 4/4, fuga, forma
  ternária composta.
- **Proposta:** essa tabela é uma **vista** que o Orelha pode gerar por parte: stems ativos (F3),
  compasso (beats), tonalidade da parte (item 21), duração. Métrica livre: sinalizar quando o
  coeficiente de variação dos intervalos entre beats numa parte passa de um limiar (madmom devolve
  beats mesmo sem pulso). Vale como painel "passagens" ao lado das partes.

### 31. Dissonância estrutural de frase e sua resolução (cap. 7, Brain Damage/Eclipse; cap. 1, GYBR)

- **Livro:** uma sequência de cadências em downbeats pares cria dissonância estrutural que pede uma
  cadência em downbeat ímpar; em Eclipse a resolução chega sílaba a sílaba na palavra "sun", com o I
  no início do hipercompasso — o clímax da peça é métrico e textual ao mesmo tempo.
- **Proposta:** derivado do item 1: uma corrida de ≥ 3 frases com cadência em downbeat fraco seguida
  de cadência em downbeat forte gera o evento `STRUCTURAL_RESOLUTION`; com letra (item 5) a palavra
  daquele instante é o que o Guide comenta. Sem pergunta pendente.

### 32. Fade-out e final fora da tônica (cap. 1, p. 28; cap. 2, p. 49)

- **Livro:** a maioria das gravações termina em fade porque a forma não tem como terminar; quando há
  acorde final, não precisa ser a tônica (Lonely People acaba no vi; Searchin' So Long num acorde de
  fora).
- **Proposta:** `Ending` por faixa ∈ {FADE_OUT, FINAL_CHORD, CUT}: fade quando a energia (F3, ou o
  RMS do mix por beat) cai monotonicamente nos últimos 10–20 s até o silêncio; senão o último
  segmento com fundamental dá o acorde final e seu grau. Métrica por década (o fade some nos anos
  2000) e por artista; gene do Genoma.

## Parte III — genes do Music Genome Project e o Genoma do Orelha

O Pandora pontua cada gravação em cerca de 450 "genes" (0–5, musicólogos treinados, 20–30 min por
faixa). A lista completa é proprietária; a tabela usa os genes que o próprio Pandora mostra ao
público em "por que esta música" e em entrevistas, com nome traduzido livremente. Ela cruza cada
gene com o conceito de Stephenson que o define e com o que o Orelha mede hoje, mede no backlog ou só
pode receber à mão.

| Gene (Pandora, público) | Grupo | Stephenson | Orelha hoje | Orelha: backlog ou manual |
|---|---|---|---|---|
| tonalidade maior / menor / mista | harmonia | modo pela tríade tônica (22), múltiplas tonalidades (21) | `key_segment` madmom + MANUAL | `KeyDeriver` (6, 20), por parte (21), `AMBIGUOUS` (22) |
| harmonia modal | harmonia | jônio/eólio/mixolídio pela melodia (19) | modos só MANUAL | `MelodicPitchSource` (19) |
| variação harmônica cromática | harmonia | sistemas menor cromático / misto (11) | fração fora do campo (eixo A) | `PaletteClassifier` (11) |
| influência de blues | harmonia | retrocessão V–IV–I, I7/IV7, terça blue (12, 19, 23) | — | índice de retrocessão (12), blue third (19), 7ª dominante como tônica (23) |
| *vamping* extenso / estrutura repetitiva | forma | antiperiódico (4), estrófico (27), vamp I–IV (24) | partes com ×N | `CycleClosure` (4), `FormClassifier` (16, 27) |
| forma verso–chorus / forma incomum | forma | quatro formas (27) | letras A, B, C | `FormClassifier` (16, 27), papéis (26) |
| uso de *fade-out* | forma | "não há como terminar" (32) | — | `Ending` (32) |
| ponte / solo instrumental | forma | ponte vs verso instrumental (26) | — | `SectionRole` SOLO/BRIDGE (26) |
| fraseado melódico repetitivo | melodia | refrão de uma linha (5), 2 + 2 (1) | — | repetição de linha (5), `PhraseModel` (1) |
| chamada e resposta vocal | vocal | resposta no 2 + 2 (3) | — | `Response` (3) |
| harmonias vocais / dueto | vocal | — | — | só manual (o stem de voz não separa vozes) |
| vocal masculino/feminino, entrega emocional, timbre da voz | vocal | — | — | só manual |
| letra explícita / romântica / narrativa / política | letra | texto como pista de forma (5) | `lyric_word` (ASR) | temas: só manual; título/refrão: (5) |
| sincopação leve/intensa | ritmo | cadência antecipada (8) | beats | antecipação da nota final (8); densidade fora do beat (F2) |
| *swing* / *shuffle* / *straight* | ritmo | — | beats (madmom) | subdivisão ternária: extrator (backlog; não é teoria, é DSP) |
| compasso 12/8 / incomum | ritmo | 6/8 e métrica livre (30) | `time_signature` madmom | métrica livre por variação de beat (30) |
| andamento / groove dançante | ritmo | — | BPM | só manual (groove) |
| guitarra elétrica com distorção / riffs | instrumentação | quinta vazia e distorção (22) | timbre por stem (`flatness`, `centroid`) | atividade por stem (F3); riff = ciclo curto sem voz (SectionDeriver) |
| instrumentação elétrica / acústica / mista | instrumentação | forma por instrumentação (14) | timbre por stem | F3 + centroide (Production) |
| linha de baixo proeminente / ocupada | instrumentação | baixo carrega a harmonia (9), baixo errado (10) | piano roll do baixo, `BassRole` | densidade de notas do baixo por compasso (barato); `WRONG_BASS` (10) |
| piano rítmico / sintetizador proeminente | instrumentação | — | stem `other` (sem separar) | 6 stems do demucs (trade-off do item 14) |
| coro / arranjo de cordas | instrumentação | coro marca o chorus (26, 29) | — | só manual |
| dinâmica agressiva / variada | produção | — | LUFS integrado | curva de RMS por parte (F3) |
| duração da faixa / estrutura longa | forma | binária composta (16), prog (30) | `duration_s` | `COMPOUND_BINARY` (16), tabela de passagens (30) |

Leitura da tabela: o que o Pandora chama de gene, Stephenson define e o Orelha mede — para
harmonia, forma e frase. O que só o ouvido humano pontua (entrega vocal, tema da letra, groove,
arranjo de coro e cordas) fica como anotação manual guiada.

### Aba Genoma (proposta registrada em 2026-09-16)

Ideia do dono: uma aba por faixa para fazer a análise ao estilo do musicólogo do Pandora, **à mão, mas
apoiada e guiada** pela análise automática do áudio.

- **O que é:** uma ficha com uma linha por gene. Cada linha mostra (a) a **evidência automática** do
  Orelha, com link para o instante na timeline (ex.: "fora do campo: 23 % · 4 partes cíclicas · IV : V =
  2,8 · 190 notas de voz sem texto"), (b) a **nota sugerida** 0–5 quando o gene é mensurável
  (`DERIVED`, com a regra visível), (c) a **nota do dono** 0–5 (`MANUAL`) e uma observação. O que não é
  mensurável mostra só (c), com a definição do gene e o trecho de Stephenson que o explica.
- **Dados:** `gene(id, key, label_pt, group, definition, stephenson_ref)` semeado por migração (lista
  fixa, ~40 genes da tabela acima, editável depois); `gene_score(track_id, gene_id, value 0–5,
  source DERIVED|MANUAL, evidence jsonb, note, updated_at)`. A nota DERIVED é recalculada ao
  re-anotar; a MANUAL sobrevive à re-análise (mesmo padrão de tonalidade, partes e letra).
- **Uso no acervo:** o vetor de genes por faixa (MANUAL quando existe, senão DERIVED) entra na
  comparação entre artistas ao lado das matrizes: distância L1 por grupo de genes, "genes que mais
  separam A de B". É o Music Genome sem os 450 anotadores, com definição publicada por gene.
- **Trade-offs:** (a) lista fixa (comparável entre faixas) × genes livres (o dono cria; incomparável
  até haver duas faixas pontuadas) — fixa primeiro; (b) escala 0–5 do Pandora (fina, subjetiva) ×
  booleano (robusto) — 0–5 para MANUAL, e DERIVED só emite 0, 3 ou 5 até as métricas terem
  calibração; (c) construir a aba antes das métricas (só anotação manual) × depois das ondas A–C
  (sugestões automáticas úteis) — a aba pode nascer manual, porque a ficha em si já organiza a
  escuta; as sugestões entram gene a gene conforme as ondas A–F avançam.
- **Onde entra:** módulo Guide (mapa de produto): é a ponte entre "ouvir e entender" e o acervo.
- **Perguntas ao dono:** (1) começar pelos ~40 genes da tabela ou por um subconjunto de harmonia e
  forma (os que já têm evidência)? (2) manter os nomes do Pandora em inglês ao lado do rótulo em
  pt-BR, para a referência ficar rastreável?
