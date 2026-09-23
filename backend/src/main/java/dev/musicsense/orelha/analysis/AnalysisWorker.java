package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.extraction.AudioExtractor;
import dev.musicsense.orelha.extraction.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Poller da fila. Claim, extração e persistência são passos separados de propósito: a chamada ao
 * extrator leva minutos e não pode segurar uma transação de banco.
 *
 * <p>Enquanto processa, o worker bate o ponto no run ({@code locked_at}) a cada
 * {@code orelha.worker.heartbeat}; um run RUNNING cujo ponto parou há mais de
 * {@code orelha.worker.stale-after} foi abandonado (o backend caiu no meio da extração — acontecia a cada
 * reinício da tarefa agendada) e volta para a fila no ciclo seguinte. O batimento é o que permite reclamar
 * em minutos sem risco de roubar o run de outro worker vivo.
 */
@Component
public class AnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    private final AnalysisQueue queue;
    private final AnalysisPipeline pipeline;
    private final AudioExtractor extractor;
    private final boolean enabled;
    private final Duration heartbeat;
    private final Duration staleAfter;
    private final int maxAttempts;
    private final int concurrency;
    /** Uma thread por análise simultânea; existe mesmo com concurrency=1 e não custa nada. */
    private final ExecutorService analyses;
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().name("orelha-heartbeat").daemon().unstarted(r));

    AnalysisWorker(AnalysisQueue queue, AnalysisPipeline pipeline, AudioExtractor extractor,
                   @Value("${orelha.worker.enabled:true}") boolean enabled,
                   @Value("${orelha.worker.heartbeat:30s}") Duration heartbeat,
                   @Value("${orelha.worker.stale-after:2m}") Duration staleAfter,
                   @Value("${orelha.worker.max-attempts:3}") int maxAttempts,
                   @Value("${orelha.worker.concurrency:1}") int concurrency) {
        this.queue = queue;
        this.pipeline = pipeline;
        this.extractor = extractor;
        this.enabled = enabled;
        this.heartbeat = heartbeat;
        this.staleAfter = staleAfter;
        this.maxAttempts = maxAttempts;
        this.concurrency = Math.max(1, concurrency);
        this.analyses = Executors.newFixedThreadPool(this.concurrency,
                r -> Thread.ofPlatform().name("orelha-analysis", 0).daemon().unstarted(r));
    }

    @Scheduled(fixedDelayString = "${orelha.worker.poll-ms:5000}")
    void poll() {
        if (!enabled) {
            return;
        }
        reclaimAbandoned();
        if (concurrency <= 1) {
            drain();
            return;
        }
        // Vários drenos ao mesmo tempo: claimNext usa FOR UPDATE SKIP LOCKED, então cada um pega um run
        // diferente. O ciclo só termina quando todos pararem, e aí o fixedDelay reprograma o próximo.
        List<Future<?>> running = IntStream.range(0, concurrency)
                .mapToObj(i -> analyses.submit(this::drain))
                .collect(Collectors.toList());
        for (Future<?> f : running) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException e) {
                log.error("um dreno da fila morreu", e.getCause());
            }
        }
    }

    /** Puxa runs enquanto houver. */
    private void drain() {
        while (pollOnce().isPresent()) {
            // drena a fila antes de dormir de novo
        }
    }

    /** Runs que ficaram órfãos de um worker morto; normalmente não há nenhum e a consulta é barata. */
    void reclaimAbandoned() {
        try {
            queue.reclaimStale(staleAfter, maxAttempts);
        } catch (Exception e) {
            log.warn("não deu para reclamar runs abandonados: {}", e.getMessage());
        }
    }

    /** Processa um run, se houver. Devolve o id processado (DONE ou FAILED). */
    public Optional<Long> pollOnce() {
        Optional<Long> claimed = queue.claimNext();
        claimed.ifPresent(this::process);
        return claimed;
    }

    private void process(long runId) {
        ScheduledFuture<?> ticking = startHeartbeat(runId);
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
        } finally {
            ticking.cancel(false);
        }
    }

    private ScheduledFuture<?> startHeartbeat(long runId) {
        long millis = Math.max(1_000, heartbeat.toMillis());
        return heartbeats.scheduleWithFixedDelay(() -> {
            try {
                queue.touch(runId);
            } catch (Exception e) {
                log.debug("run {}: batimento falhou ({})", runId, e.getMessage());
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
    }
}
