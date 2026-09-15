package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.extraction.AudioExtractor;
import dev.musicsense.orelha.extraction.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Poller da fila. Claim, extração e persistência são passos separados de propósito: a chamada ao
 * extrator leva minutos e não pode segurar uma transação de banco.
 */
@Component
public class AnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    private final AnalysisQueue queue;
    private final AnalysisPipeline pipeline;
    private final AudioExtractor extractor;
    private final boolean enabled;

    AnalysisWorker(AnalysisQueue queue, AnalysisPipeline pipeline, AudioExtractor extractor,
                   @Value("${orelha.worker.enabled:true}") boolean enabled) {
        this.queue = queue;
        this.pipeline = pipeline;
        this.extractor = extractor;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${orelha.worker.poll-ms:5000}")
    void poll() {
        if (enabled) {
            while (pollOnce().isPresent()) {
                // drena a fila antes de dormir de novo
            }
        }
    }

    /** Processa um run, se houver. Devolve o id processado (DONE ou FAILED). */
    public Optional<Long> pollOnce() {
        Optional<Long> claimed = queue.claimNext();
        claimed.ifPresent(this::process);
        return claimed;
    }

    private void process(long runId) {
        try {
            AnalysisPipeline.Input input = pipeline.load(runId);
            log.info("run {}: extracting {}", runId, input.audioPath());
            ExtractionResult result = extractor.analyze(Path.of(input.audioPath()), input.audioSha256());
            pipeline.persist(runId, result);
            queue.complete(runId);
            log.info("run {}: done ({} chord segments)", runId, result.chords().size());
        } catch (Exception e) {
            log.error("run {}: failed", runId, e);
            queue.fail(runId, e);
        }
    }
}
