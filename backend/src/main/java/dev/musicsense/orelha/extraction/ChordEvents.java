package dev.musicsense.orelha.extraction;

import dev.musicsense.orelha.extraction.ExtractionResult.ChordEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Operações sobre a sequência bruta de acordes antes da normalização. */
public final class ChordEvents {

    private ChordEvents() {
    }

    /**
     * Funde segmentos consecutivos com o mesmo acorde (fundamental, qualidade e baixo): a segmentação
     * do extrator não é ritmo harmônico. Chromas fundidos = média ponderada pela duração.
     */
    public static List<ChordEvent> mergeConsecutive(List<ChordEvent> events) {
        List<ChordEvent> merged = new ArrayList<>();
        for (ChordEvent event : events) {
            ChordEvent last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.chord().equals(event.chord())) {
                merged.set(merged.size() - 1, join(last, event));
            } else {
                merged.add(event);
            }
        }
        return merged;
    }

    private static ChordEvent join(ChordEvent a, ChordEvent b) {
        double da = duration(a);
        double db = duration(b);
        Float confidence = a.confidence() == null || b.confidence() == null ? null
                : Math.min(a.confidence(), b.confidence());
        return new ChordEvent(a.startS(), b.endS(), a.chord(),
                weighted(a.chroma(), da, b.chroma(), db),
                weighted(a.chromaLow(), da, b.chromaLow(), db),
                confidence);
    }

    private static float[] weighted(float[] a, double da, float[] b, double db) {
        if (a == null || b == null) {
            return a != null ? a : b;
        }
        double total = da + db;
        float[] out = new float[12];
        for (int i = 0; i < 12; i++) {
            out[i] = total == 0 ? (a[i] + b[i]) / 2 : (float) ((a[i] * da + b[i] * db) / total);
        }
        return out;
    }

    private static double duration(ChordEvent e) {
        return e.endS().subtract(e.startS()).max(BigDecimal.ZERO).doubleValue();
    }
}
