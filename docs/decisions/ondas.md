# Ondas entregues

O histórico das ondas e os portões. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- [x] Onda 0 — esqueleto (entregue e aprovado em 2026-09-13)
- [x] Onda 1 — `HarmonicNormalizer`: validado contra as 30 progressões do dono
  (`ProgressionClassificationTest`); portão aprovado em 2026-09-13 com estas convenções:
  power chord em caixa alta neutra (`I5`); `AMBIGUOUS` conta como *dentro* do campo nas métricas
  do acervo; `7sus4` reduz a `sus4`; modos (mixolídio/dórico de blues, frígio) só existem se
  atribuídos — `key_segment.source = MANUAL` ou heurística futura (backlog Onda 3).
- [x] Onda 2 — extrator próprio em `extractor/` (0.2.0), adapter, fila, worker, pipeline até
  `harmonic_annotation`, endpoints de run/timeline/key/canonical-run; contract test com a resposta
  real do container. Portão aprovado em 2026-09-14 com Valerie, Smells Like Teen Spirit e Creep:
  acordes e transições batem com o ouvido do dono; power chord não é inferível (ver Extração);
  tonalidade de baixa confiança corrigida por override manual.
- [ ] Onda 3 — analítica do acervo, entregue em 2026-09-14 (portão pendente): `GET
  /api/collection/artists/{id}/profile`, `/albums/{id}/profile`, `/compare?a&b`,
  `/artists/{id}/pedal-passages?relation=`. Perfil = eixo A por contagem e duração, fração fora do
  campo (`AMBIGUOUS` conta como dentro), distribuição de graus (12 bins, grafia neutra `♯IV/♭V`) +
  entropia de Shannon em bits, matriz de transição 12×12 (`counts`, `rowNormalized`), relações do
  eixo B, timbre médio por álbum e stem. Comparação = JS divergence (bits, base 2) das matrizes
  normalizadas globalmente + L1 de graus e do eixo A. Uma query nativa (`CollectionQueries`) sobre run
  canônico + tonalidade preferida; métricas em Java puro (`CollectionMetrics`). A diagonal da matriz
  existe e significa troca de qualidade/baixo sobre a mesma fundamental (`IV → iv` do Creep).
  (+ backlog: tonalidade `DERIVED` por perfil de fundamentais
  quando a confiança do madmom for baixa; heurística de modo por I7/IV7 recorrentes; query de
  linha de baixo sob acorde sustentado — Valerie 3:18, Kashmir; `min_segment_duration` do BTC
  vs segmentos de < 1 s)
- [x] Onda 4 — Angular, entregue e aprovada em 2026-09-14: acervo, timeline com playback
  sincronizado e lane de baixo efetivo, perfil com heatmap/barras/timbre/pedais, comparação com
  distâncias. Verificado ao vivo contra os runs reais (Creep, Nirvana × Radiohead).
