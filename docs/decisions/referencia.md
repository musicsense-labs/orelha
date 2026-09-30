# Referência humana (TheoryTab e Hooktheory)

A análise humana como régua para o extrator. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

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
