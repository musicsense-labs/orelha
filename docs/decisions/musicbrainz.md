# Identidade no MusicBrainz

Ano do release-group, o casamento pontuado e o lote. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- **Por que**: o acervo tinha 17 dos 30 álbuns com nome de box set e ano 2009 — os Beatles inteiros de 1963–70
  datados pela remasterização. Como o projeto compara vocabulário **entre eras**, o eixo do tempo estava
  errado em mais da metade do acervo. Dados do MusicBrainz são CC0.
- **Ano que vale é o do release-group** (decisão do dono em 2026-09-22): `album.year` continua sendo o que as
  tags disseram (a edição no disco), `album.first_released` é a primeira edição do release-group, e
  `Album.effectiveYear()` = `first_released ?? year`. Ordenação do acervo e timbre por álbum usam
  `COALESCE(first_released, year)`; a UI mostra o efetivo e explica a edição no `title`. V13 acrescenta
  `mbid` em `artist`/`album`, `first_released` e `metadata_source` (TAGS/MUSICBRAINZ/MANUAL) — sem UNIQUE no
  mbid de propósito, porque um box set partido em CD1/CD2 são dois álbuns nossos para um release-group.
- **Pacote `metadata`**: `MusicBrainzClient` (ws/2, `User-Agent` com contato obrigatório — `ORELHA_MUSICBRAINZ_CONTACT`
  no `.env`; 1 req/s serializada no processo; **URI pronta, nunca String**, senão o RestClient re-codifica o
  `%3A` da query Lucene e a busca volta vazia; busca sem aspas quando a frase exata não acha nada, que é o caso
  de "Sgt. Pepper's" × "Sgt. Peppers"). `AlbumMatcher` (Java puro) limpa o título do box set e pontua:
  semelhança de título (peso 2) e de artista (peso 1), −0,15 fora de Album/EP, **−0,25 em secondary-type
  Compilation/Live/Bootleg** (Soundtrack não penaliza: Help! e A Hard Day's Night são trilhas), −0,18 em
  desambiguação de "live/demo/tribute", e o artista funciona como porta (abaixo de 0,85 multiplica o total —
  "Beatles Tribute Band" contém "Beatles" e não passa por eles).
- **Sempre sob demanda, o dono confirma**: `GET/PUT/DELETE /api/albums/{id}/musicbrainz[/candidates]`; painel
  no perfil do álbum com a lista pontuada e "desfazer". `MANUAL` não é sobrescrito. Lote em
  `docs/musicbrainz-sync.mjs` (`--apply`; sem isso só simula) aplica **só acima de 0,98 e sozinho** — a marca
  "confiante" da tela é 0,85, mas gravar sem ninguém olhar exige mais: "The Beatles (White Album)" de 2000
  aparece com 95 % e seria um erro silencioso. Medido em 2026-09-22: 22 dos 30 álbuns aplicados a 100 %
  (12 com era corrigida, os Beatles de 2009 → 1963–70), 7 ambíguos para a tela (White Album, Past Masters,
  Magical Mystery Tour Album × EP, Sgt. Pepper, Morning Glory) e 1 sem resultado ("CD1", nome irrecuperável).
- **Próximo (onda B, não implementado)**: impressão digital de áudio (Chromaprint `fpcalc` no extrator, que é
  DSP, + AcoustID no núcleo) para identificar faixa a faixa quando a tag não presta, `recording`/`work` MBID
  por faixa, e daí comparar gravações da mesma obra (Valerie dos Zutons × Amy Winehouse).
