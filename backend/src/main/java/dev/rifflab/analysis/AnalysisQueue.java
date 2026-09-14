package dev.rifflab.analysis;

import dev.rifflab.catalog.Track;
import dev.rifflab.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** A fila é a tabela analysis_run; cada método é uma transação curta. */
@Service
public class AnalysisQueue {

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

    @Transactional
    public void fail(long runId, Throwable error) {
        AnalysisRun run = find(runId);
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
