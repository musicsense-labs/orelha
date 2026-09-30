# A fila de análise

analysis_run como fila, batimento, reclamação, faxina de órfãos, pausa e pausa automática. Registro das decisões e medições; o que vale hoje, em resumo, está no CLAUDE.md da raiz.

- **A fila é `analysis_run`** (`status`, `attempts`, `locked_at`), poller `@Scheduled`
  com `SELECT … FOR UPDATE SKIP LOCKED`. Sem Kafka, sem Redis. **Batimento e reclamação (2026-09-23)**: enquanto
  extrai (minutos), o worker atualiza `locked_at` a cada `orelha.worker.heartbeat` (30 s) numa thread própria;
  todo ciclo do poller reclama os RUNNING cujo ponto parou há mais de `stale-after` (2 min) — de volta a QUEUED
  até `max-attempts` (3), depois FAILED com a razão. É o conserto do run que ficava travado para sempre quando
  o backend caía no meio da extração (aconteceu três vezes entre 18 e 23/09: a faixa não podia ser excluída,
  respondia 409, e perdia o botão de reprocessar). O batimento é o que permite reclamar em minutos sem roubar
  o run de outro worker vivo — a fila continua válida para vários processos. **Faxina de órfãos** (2026-09-23,
  `OrphanSweeper`): a cada `orelha.data.orphan-sweep-interval` (6 h) apaga pasta de stems ou Parquet cujo SHA
  não é de faixa nenhuma — sobra que o extrator deixa quando grava antes do run ser persistido e o run acaba
  abandonado. Três travas, porque apaga sozinho: acervo vazio aborta (banco fora do ar não vira faxina geral),
  só entra o que está parado há mais de `orphan-min-age` (1 h, nunca disputa com análise em andamento) e nada
  fora de `stems`/`features` é olhado (um .txt na pasta fica). `POST /api/admin/orphans` (só administrador)
  mostra o que há (`dryRun=true`, padrão) ou limpa na hora. **Pausar a fila sem derrubar o Orelha** (2026-09-24):
  o extrator ocupa ~13 das 28 threads por horas e nem sempre é hora disso. `POST /api/admin/worker?enabled=false`
  (`GET` devolve `{enabled, queued, running}`; painel "Fila de análise" na aba administrador) pausa o consumo
  **em execução**: a flag é lida **dentro do laço de dreno**, não só na entrada do poller — com a fila grande
  um único ciclo dura dias, e uma pausa que só olhasse a entrada não faria nada. Pausar não aborta a faixa que
  já está no extrator: ela termina e é gravada. O reclaim de abandonados continua rodando pausado (um RUNNING
  preso impediria até excluir a faixa). Vale só para o processo: reiniciar volta ao `orelha.worker.enabled`
  (`ORELHA_WORKER_ENABLED` no `.env` é o padrão de quem quer subir pausado). Alívio sem reiniciar nada:
  `docker update --cpus=3 orelha-extractor` limita o container em execução (`--cpus=0` solta; recriar o
  container também).

**Pausa automática (2026-09-30).** Em 2026-09-26 o disco D: sumiu por dois minutos e o worker marcou como FAILED as
1581 faixas que faltavam, uma a cada 80 ms: toda exceção era terminal, e nada distinguia máquina quebrada de faixa
ruim. Agora, depois de `orelha.worker.pause-after-failures` (3) falhas seguidas, o worker se pausa sozinho e guarda o
motivo; um sucesso zera a contagem. O painel da aba administrador mostra o motivo e um botão que devolve à fila
toda faixa cujo run mais recente falhou (`POST /api/admin/worker/requeue-failed`: tentativas zeradas, ordem original).
A concorrência do worker (`orelha.worker.concurrency`) saiu no mesmo dia: medida como mais lenta nesta máquina.
