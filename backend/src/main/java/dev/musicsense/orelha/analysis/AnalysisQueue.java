package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A fila é a tabela analysis_run; cada método é uma transação curta. */
@Service
public class AnalysisQueue {

    private static final Logger log = LoggerFactory.getLogger(AnalysisQueue.class);

    private final AnalysisRunRepository runs;

    AnalysisQueue(AnalysisRunRepository runs) {
        this.runs = runs;
    }

    @Transactional
    public AnalysisRun enqueue(Track track, String extractorName) {
        return runs.save(new AnalysisRun(track, extractorName, null, Map.of()));
    }

    @Transactional
    public Optional<Long> claimNext() {
        return runs.lockNextQueued().map(run -> {
            run.setStatus(RunStatus.RUNNING);
            run.setAttempts(run.getAttempts() + 1);
            run.setLockedAt(Instant.now());
            run.setStartedAt(Instant.now());
            run.setError(null);
            return run.getId();
        });
    }

    @Transactional
    public void complete(long runId) {
        AnalysisRun run = find(runId);
        run.setStatus(RunStatus.DONE);
        run.setLockedAt(null);
        run.setFinishedAt(Instant.now());
    }

    /**
     * Devolve à fila (ou dá por perdido) os runs que ficaram em RUNNING sem ninguém trabalhando neles — o
     * caso do backend reiniciado no meio da extração, que acontecia toda vez que a tarefa agendada subia de
     * novo: a faixa ficava sem poder ser excluída (409) e sem botão de reprocessar na tela. Abandonado é
     * quem parou de bater o ponto (locked_at) há mais de {@code staleAfter}; enquanto trabalha, o worker
     * mantém o batimento. Volta para QUEUED até {@code maxAttempts} tentativas; depois vira FAILED, para
     * não ficar em laço num áudio que sempre derruba o extrator. Devolve quantos foram reclamados.
     */
    @Transactional
    public int reclaimStale(Duration staleAfter, int maxAttempts) {
        List<AnalysisRun> stale = runs.lockStaleRunning(Instant.now().minus(staleAfter));
        for (AnalysisRun run : stale) {
            if (run.getAttempts() < maxAttempts) {
                run.setStatus(RunStatus.QUEUED);
                run.setLockedAt(null);
                run.setStartedAt(null);
                run.setError(null);
                log.warn("run {}: abandonado em execução (tentativa {}); de volta à fila", run.getId(), run.getAttempts());
            } else {
                run.setStatus(RunStatus.FAILED);
                run.setLockedAt(null);
                run.setFinishedAt(Instant.now());
                run.setError("Abandonado em execução " + maxAttempts + " vezes (o backend caiu durante a extração?).");
                log.warn("run {}: abandonado pela {}ª vez; marcado como falho", run.getId(), run.getAttempts());
            }
        }
        return stale.size();
    }

    /** Batimento do run em execução: enquanto isto acontece, ninguém o considera abandonado. */
    @Transactional
    public void touch(long runId) {
        runs.touch(runId, Instant.now());
    }

    /** Registra a falha; um run que sumiu no meio (faixa excluída enquanto o worker rodava) só vira aviso. */
    @Transactional
    public void fail(long runId, Throwable error) {
        AnalysisRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            log.warn("run {}: não existe mais; falha descartada ({})", runId, error.toString());
            return;
        }
        run.setStatus(RunStatus.FAILED);
        run.setLockedAt(null);
        run.setFinishedAt(Instant.now());
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        run.setError(message.length() > 2000 ? message.substring(0, 2000) : message);
    }

    /**
     * Devolve à fila as faixas cujo run mais recente falhou — o conserto depois de uma queda do disco ou do
     * extrator. Zera as tentativas e mantém a ordem original de pedido. Devolve quantas voltaram.
     */
    @Transactional
    public int requeueFailed() {
        int count = runs.requeueLatestFailed();
        log.info("{} faixas com o último run falho voltaram à fila", count);
        return count;
    }

    public AnalysisRun find(long runId) {
        return runs.findById(runId).orElseThrow(() -> new NotFoundException("AnalysisRun", runId));
    }
}
