# Referência humana × Orelha: 24 faixas do TheoryTab (2026-09-16)

Medição feita com `GET /api/tracks/{id}/reference/compare` depois de sincronizar a análise pública do
TheoryTab (Hooktheory) de cada faixa: `node docs/theorytab-sync.mjs batch docs/theorytab-tracks.tsv`
baixa a página de cada música, lê a tabela "About the Chord Progressions" (tonalidade em `data-tonic`/
`data-scale`, seções e numerais no link `advanced-search?chordString=`) e grava via `PUT /reference`.
As páginas de música são públicas; só as listas por artista pedem login. Nosso lado: run canônico
(extrator 0.5.0, Creep em 0.6.0), tonalidade preferida (MANUAL > EXTRACTOR), partes DERIVED.

Convenções da comparação: numerais do TheoryTab lidos **na escala do modo declarado** (III em Fá menor é
Lá♭); power chords "(no3)" e sus reduzem a maior dos dois lados; 7ª, inversão e sus ignorados.
**Sequência** = 1 − distância de edição / tamanho (repetições consecutivas fundidas), por seção da
referência contra a parte nossa mais parecida, média das seções. **Vocabulário** = fração dos acordes da
referência que aparecem nessa parte.

| track | música | TheoryTab | tonalidade | sequência | vocabulário | partes nossas |
|---|---|---|---|---|---|---|
| 4 | creep | G major | tom ✓ | 29% | 100% | ABC |
| 3 | smells-like-teen-spirit | F minor | tom ✓ | 42% | 90% | ABCDEFCGH |
| 7 | born-to-run | E major | tom ✓ | 63% | 100% | ABCDEFGHIJBKLM |
| 37 | let-it-be | C major | tom ✓ | 26% | 73% | ABCDE |
| 30 | hey-jude | F major | tom ✓ | 49% | 89% | ABCDEFGH |
| 69 | come-together | D dorian | tom ✓ | 50% | 100% | ABCDCECFCGHIAJ |
| 70 | something | C major | tom ✓ | 24% | 100% | AB |
| 75 | here-comes-the-sun | A major | tom ✓ | 58% | 92% | ABCDEFG |
| 193 | yesterday | F major | tom ✓ | 24% | 100% | ABC |
| 181 | help | A mixolydian | tom ✓ | 67% | 87% | ABCDEFGCDEFGCDEH |
| 44 | i-want-to-hold-your-hand | G major | tom ✓ | 26% | 100% | ABCADAEF |
| 209 | a-hard-days-night | G mixolydian | tom ✓ | 41% | 100% | ABCBDE |
| 202 | eight-days-a-week | D major | tom ✓ | 52% | 85% | ABCDEFGC |
| 23 | twist-and-shout | D major | tom ✓ | 75% | 100% | ABCAC |
| 123 | blackbird | G major | tom ✓ | 21% | 65% | ABACD |
| 119 | while-my-guitar-gently-weeps | A minor | tom ✓ | 44% | 70% | ABCBADE |
| 177 | in-my-life | A major | tom ✓ | 49% | 100% | ABCDE |
| 154 | eleanor-rigby | E minor | tom ✓ | 83% | 100% | ABCDCEFC |
| 137 | penny-lane | B major | tom ✓ | 15% | 100% | ABCBDE |
| 136 | strawberry-fields-forever | Bb major | tom ✓ | 5% | 93% | ABC |
| 142 | lucy-in-the-sky-with-diamonds | A mixolydian | tom ✗ | 31% | 78% | ABCBDEB |
| 215 | cant-buy-me-love | C major | tom ✓ | 72% | 100% | ABCDCECFGCHIJ |
| 187 | ticket-to-ride | A major | tom ✓ | 33% | 100% | ABACDAEDAFA |
| 239 | nib | E minor | tom ✓ | 40% | 100% | ABCDEDFGHDIJKL |

**Totais:** 24 faixas · tônica bate 23/24 · modo bate 23/24 · sequência média 43 % · vocabulário médio 93 %.

## Leitura

- **Tonalidade**: 23/24. A única divergência é Lucy in the Sky: madmom diz Ré maior, o TheoryTab diz Lá
  mixolídio, o mesmo conjunto de notas com a tônica decidida pela melodia (Stephenson, itens 19 e 20 do
  backlog). É exatamente o caso que o `KeyDeriver` com evidência melódica resolveria.
- **Vocabulário alto, sequência baixa** é o padrão (Something 100 % / 24 %, Yesterday 100 % / 24 %, Penny
  Lane 100 % / 15 %): o BTC acerta os acordes, mas o `SectionDeriver` não fecha o ciclo e entrega uma parte
  longa (Strawberry Fields: uma parte A de 89 acordes contra ciclos de 5 no TheoryTab; Something: duas
  partes para a música inteira). O gargalo de hoje está nas **partes**, não nos acordes.
- **Acordes que faltam de verdade**: diminutos (Blackbird vii°, While My Guitar Gently Weeps ♯vi°,
  Strawberry Fields) e o iv / ii de Let It Be. O BTC tende a rotular diminutos como o maior ou menor vizinho
  e a perder acordes de passagem curtos, consistente com o `min_segment_duration` do backlog da Onda 3.
- **Melhores casos** (Eleanor Rigby 83 %, Twist and Shout 75 %, Can't Buy Me Love 72 %, Help 67 %) são
  músicas de ciclo curto e tríades limpas: onde o derivador fecha o ciclo, a sequência bate.
- **Teen Spirit** subiu de 5 % para 42 % ao ler os numerais na escala do modo: a primeira rodada lia
  "III VI" em Fá menor como Lá e Ré. É o erro de convenção que o parser agora evita.

## Próximos passos sugeridos

1. `SectionDeriver`: aceitar variação no último acorde do ciclo (IV ↔ iv, IVsus4 → IV), que o TheoryTab
   trata como o mesmo ciclo de 4 a 5 acordes; hoje as bordas são estritas. Medir de novo aqui.
2. Usar estes 24 pares como teste de regressão: rodar a comparação a cada mudança do derivador ou do
   normalizador, e a tabela vira um número por commit.
3. Faixas sem página no TheoryTab (Valerie dos Zutons, Norwegian Wood, Black Sabbath, Evil Woman): colar à
   mão no painel ou pular.
