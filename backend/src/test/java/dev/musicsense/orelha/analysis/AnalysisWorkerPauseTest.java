package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.extraction.AudioExtractor;
import dev.musicsense.orelha.extraction.ExtractionResult;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pausar o consumo da fila tem de ser visto <em>dentro</em> do laço de dreno: com a fila grande, um único
 * ciclo do poller dura dias, e a pausa que só olhasse a entrada não teria efeito nenhum.
 */
class AnalysisWorkerPauseTest {

    private final AnalysisQueue queue = mock(AnalysisQueue.class);
    private final AnalysisPipeline pipeline = mock(AnalysisPipeline.class);
    private final AudioExtractor extractor = mock(AudioExtractor.class);

    private AnalysisWorker worker(boolean enabled) {
        return new AnalysisWorker(queue, pipeline, extractor, enabled,
                Duration.ofSeconds(30), Duration.ofMinutes(2), 3, 3);
    }

    @Test
    void pausingDuringATrackFinishesItAndStopsBeforeTheNext() {
        AnalysisWorker worker = worker(true);
        ExtractionResult result = mock(ExtractionResult.class);
        when(result.chords()).thenReturn(List.of());
        when(queue.claimNext()).thenReturn(Optional.of(7L), Optional.of(8L));
        when(pipeline.load(7L)).thenAnswer(invocation -> {
            worker.enabled(false);   // o administrador aperta "pausar" enquanto esta faixa está no extrator
            return new AnalysisPipeline.Input("audio.mp3", "sha");
        });
        when(extractor.analyze(any(), any())).thenReturn(result);

        worker.poll();

        verify(pipeline).persist(7L, result);   // a faixa em andamento termina e é gravada
        verify(queue).complete(7L);
        verify(queue, times(1)).claimNext();    // e o dreno para: a 8 fica na fila
        verify(pipeline, never()).load(8L);
        assertThat(worker.enabled()).isFalse();
    }

    @Test
    void pausedWorkerStillReclaimsButTakesNothing() {
        AnalysisWorker worker = worker(false);

        worker.poll();

        verify(queue, never()).claimNext();
        // Reclamar não é consumir: um run abandonado preso em RUNNING impediria até excluir a faixa.
        verify(queue).reclaimStale(any(), anyInt());
    }

    @Test
    void threeFailuresInARowPauseTheWorkerInsteadOfEmptyingTheQueue() {
        // 26/09: o disco sumiu e cada run falhava em 80 ms — sem a pausa, a fila inteira virou FAILED.
        AnalysisWorker worker = worker(true);
        when(queue.claimNext()).thenReturn(Optional.of(1L), Optional.of(2L), Optional.of(3L), Optional.of(4L));
        when(pipeline.load(anyLong())).thenReturn(new AnalysisPipeline.Input("D:/orelha/1/a.mp3", "sha"));
        when(extractor.analyze(any(), any())).thenThrow(new IllegalStateException("No such device\nsegunda linha"));

        worker.poll();

        verify(queue, times(3)).fail(anyLong(), any());
        verify(pipeline, never()).load(4L);                 // a quarta ficou na fila
        assertThat(worker.enabled()).isFalse();
        assertThat(worker.pausedBecause())
                .contains("3 falhas seguidas", "run 3", "IllegalStateException: No such device")
                .doesNotContain("segunda linha");
    }

    @Test
    void aSuccessBetweenFailuresResetsTheCount() {
        AnalysisWorker worker = worker(true);
        ExtractionResult result = mock(ExtractionResult.class);
        when(result.chords()).thenReturn(List.of());
        when(queue.claimNext()).thenReturn(Optional.of(1L), Optional.of(2L), Optional.of(3L), Optional.of(4L),
                Optional.of(5L), Optional.empty());
        when(pipeline.load(anyLong())).thenReturn(new AnalysisPipeline.Input("a.mp3", "sha"));
        when(extractor.analyze(any(), any()))
                .thenThrow(new IllegalStateException("faixa ruim"))
                .thenThrow(new IllegalStateException("faixa ruim"))
                .thenReturn(result)
                .thenThrow(new IllegalStateException("faixa ruim"))
                .thenThrow(new IllegalStateException("faixa ruim"));

        worker.poll();

        verify(queue, times(4)).fail(anyLong(), any());   // duas e duas, nunca três seguidas
        assertThat(worker.enabled()).isTrue();
        assertThat(worker.pausedBecause()).isNull();
    }

    @Test
    void resumingClearsTheReason() {
        AnalysisWorker worker = worker(true);
        when(queue.claimNext()).thenReturn(Optional.of(1L), Optional.of(2L), Optional.of(3L));
        when(pipeline.load(anyLong())).thenReturn(new AnalysisPipeline.Input("a.mp3", "sha"));
        when(extractor.analyze(any(), any())).thenThrow(new IllegalStateException("extrator fora"));
        worker.poll();
        assertThat(worker.pausedBecause()).isNotNull();

        worker.enabled(true);

        assertThat(worker.pausedBecause()).isNull();
    }

    @Test
    void resumingPutsItBackToWork() {
        AnalysisWorker worker = worker(false);
        when(queue.claimNext()).thenReturn(Optional.empty());

        worker.enabled(true);
        worker.poll();

        assertThat(worker.enabled()).isTrue();
        verify(queue).claimNext();
    }
}
