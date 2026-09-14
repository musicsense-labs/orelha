package dev.rifflab.extraction;

import dev.rifflab.extraction.ExtractionResult.ChordEvent;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Operações sobre a sequência bruta de acordes antes da normalização. */
public final class ChordEvents {

    private ChordEvents() {
    }

    /**
     * Funde segmentos consecutivos com o mesmo acorde (fundamental, qualidade e baixo): a segmentação
     * do extrator não é ritmo harmônico. Chroma fundido = média ponderada pela duração.
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
        float[] chroma = weightedChroma(a, b);
        Float confidence = a.confidence() == null || b.confidence() == null ? null
                : Math.min(a.confidence(), b.confidence());
        return new ChordEvent(a.startS(), b.endS(), a.chord(), chroma, confidence);
    }

    private static float[] weightedChroma(ChordEvent a, ChordEvent b) {
        if (a.chroma() == null || b.chroma() == null) {
            return a.chroma() != null ? a.chroma() : b.chroma();
        }
        double da = duration(a);
        double db = duration(b);
        double total = da + db;
        float[] out = new float[12];
        for (int i = 0; i < 12; i++) {
            out[i] = total == 0 ? (a.chroma()[i] + b.chroma()[i]) / 2
                    : (float) ((a.chroma()[i] * da + b.chroma()[i] * db) / total);
        }
        return out;
    }

    private static double duration(ChordEvent e) {
        return e.endS().subtract(e.startS()).max(BigDecimal.ZERO).doubleValue();
    }
}
