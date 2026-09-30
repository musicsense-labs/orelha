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
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Poller da fila. Claim, extração e persistência são passos separados de propósito: a chamada ao
 * extrator leva minutos e não pode segurar uma transação de banco.
 *
 * <p>Enquanto processa, o worker bate o ponto no run ({@code locked_at}) a cada
 * {@code orelha.worker.heartbeat}; um run RUNNING cujo ponto parou há mais de
 * {@code orelha.worker.stale-after} foi abandonado (o backend caiu no meio da extração — acontecia a cada
 * reinício da tarefa agendada) e volta para a fila no ciclo seguinte. O batimento é o que permite reclamar
 * em minutos sem risco de roubar o run de outro worker vivo.
 *
 * <p>Falhas seguidas não são da faixa, são da máquina: em 2026-09-26 o disco D: sumiu por dois minutos e o
 * worker marcou como FAILED as 1581 faixas que faltavam, uma a cada 80 ms. Depois de
 * {@code orelha.worker.pause-after-failures} falhas seguidas ele se pausa sozinho e diz por quê; o
 * administrador confere disco e extrator, devolve as falhas à fila e retoma.
 */
@Component
public class AnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

    private final AnalysisQueue queue;
    private final AnalysisPipeline pipeline;
    private final AudioExtractor extractor;
    /** Consumir a fila ou não; o administrador liga e desliga em execução (a extração come a CPU da máquina). */
    private volatile boolean enabled;
    private final Duration heartbeat;
    private final Duration staleAfter;
    private final int maxAttempts;
    private final int pauseAfterFailures;
    private final AtomicInteger failuresInARow = new AtomicInteger();
    /** Por que o worker se pausou sozinho; null quando está ligado ou foi pausado pelo administrador. */
    private volatile String pausedBecause;
    private final ScheduledExecutorService heartbeats =
            Executors.newSingleThreadScheduledExecutor(r -> Thread.ofPlatform().name("orelha-heartbeat").daemon().unstarted(r));

    AnalysisWorker(AnalysisQueue queue, AnalysisPipeline pipeline, AudioExtractor extractor,
                   @Value("${orelha.worker.enabled:true}") boolean enabled,
                   @Value("${orelha.worker.heartbeat:30s}") Duration heartbeat,
                   @Value("${orelha.worker.stale-after:2m}") Duration staleAfter,
                   @Value("${orelha.worker.max-attempts:3}") int maxAttempts,
                   @Value("${orelha.worker.pause-after-failures:3}") int pauseAfterFailures) {
        this.queue = queue;
        this.pipeline = pipeline;
        this.extractor = extractor;
        this.enabled = enabled;
        this.heartbeat = heartbeat;
        this.staleAfter = staleAfter;
        this.maxAttempts = maxAttempts;
        this.pauseAfterFailures = pauseAfterFailures;
    }

    @Scheduled(fixedDelayString = "${orelha.worker.poll-ms:5000}")
    void poll() {
        reclaimAbandoned();   // mesmo pausado: run abandonado preso em RUNNING não deixa excluir a faixa
        if (!enabled) {
            return;
        }
        drain();
    }

    /**
     * Puxa runs enquanto houver — e enquanto o administrador deixar: com a fila grande este laço dura dias
     * dentro de um único ciclo do poller, então é aqui que a pausa precisa ser vista, não só na entrada.
     */
    private void drain() {
        while (enabled && pollOnce().isPresent()) {
            // drena a fila antes de dormir de novo
        }
    }

    /** O que o administrador vê e muda: consumir a fila ou deixá-la parada. */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Liga e desliga o consumo da fila em execução. Desligar <b>não aborta</b> a faixa que já está no
     * extrator — ela termina e é gravada; o worker é que não puxa a próxima. Vale só para este processo:
     * reiniciar o backend volta ao {@code orelha.worker.enabled} do ambiente.
     */
    public void enabled(boolean value) {
        if (enabled != value) {
            log.info("consumo da fila {}", value ? "retomado" : "pausado (a faixa em análise termina)");
        }
        failuresInARow.set(0);
        pausedBecause = null;
        enabled = value;
    }

    /** Por que o worker se pausou sozinho (falhas seguidas); null se não foi isso. */
    public String pausedBecause() {
        return pausedBecause;
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
            failuresInARow.set(0);
            log.info("run {}: done ({} chord segments)", runId, result.chords().size());
        } catch (Exception e) {
            log.error("run {}: failed", runId, e);
            queue.fail(runId, e);
            afterFailure(runId, e);
        } finally {
            ticking.cancel(false);
        }
    }

    /** Conta a falha; na sequência que passa do limite, pausa o consumo em vez de esvaziar a fila. */
    private void afterFailure(long runId, Exception e) {
        int streak = failuresInARow.incrementAndGet();
        if (pauseAfterFailures > 0 && streak >= pauseAfterFailures && enabled) {
            String message = String.valueOf(e.getMessage()).lines().findFirst().orElse("");
            pausedBecause = streak + " falhas seguidas; a última (run " + runId + "): " + e.getClass().getSimpleName()
                    + ": " + (message.length() > 200 ? message.substring(0, 200) + "…" : message);
            enabled = false;
            log.warn("consumo da fila pausado sozinho — {}. Confira disco e extrator e retome na aba administrador.",
                    pausedBecause);
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
