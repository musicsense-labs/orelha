# Partes da música

A tabela section, o SectionDeriver e a edição manual. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- **Tabela `section`** por run: `start_s`, `end_s`, `cycle_end_s` (fim da 1ª repetição do ciclo),
  `repeats`, `label`, `source` ∈ {DERIVED, EXTRACTOR, MANUAL}; leitura prefere MANUAL > EXTRACTOR >
  DERIVED (como `key_segment`). `GET /api/tracks/{id}/sections[?runId]` devolve cada parte com a
  progressão de um ciclo anotada (cifra, grau, eixo A); `PUT` grava a edição do dono (lista vazia
  volta à derivação; `cycleEndS`/`repeats` opcionais mantêm o "×N"); `POST …/sections/derive`
  re-deriva sem tocar nas MANUAL. Como a tonalidade, a edição MANUAL vive no run; desde 2026-09-16 a re-análise
  copia as partes MANUAL do canônico anterior para o run novo. (Help! tinha partes MANUAL num run 0.3.0 que não
  chegaram ao canônico 0.6.0 — as re-análises anteriores à herança não as levaram; em 2026-09-30 a cópia ficou
  como comando para o dono rodar, junto com a limpeza dos runs antigos.)
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
