package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.common.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    public AnalysisRun find(long runId) {
        return runs.findById(runId).orElseThrow(() -> new NotFoundException("AnalysisRun", runId));
    }
}
