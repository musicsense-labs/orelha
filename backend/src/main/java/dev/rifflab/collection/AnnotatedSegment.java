package dev.rifflab.collection;

import dev.rifflab.harmony.ChordQuality;

/**
 * Um segmento de acorde já anotado, como sai da query do acervo (run canônico, tonalidade preferida).
 * Entrada pura de {@link CollectionMetrics}; a ordem esperada é (trackId, seqNo).
 */
public record AnnotatedSegment(long trackId, String trackTitle, int seqNo, double startS, double endS,
                               Integer rootPc, ChordQuality quality, Integer degreeInterval, String degreeLabel,
                               String keyRelation, String relationFromPrev, Integer effectiveBassPc) {

    public double duration() {
        return Math.max(0, endS - startS);
    }

    public boolean hasDegree() {
        return degreeInterval != null;
    }

    /** O segmento anterior na mesma faixa, quando existe: base de toda transição. */
    public boolean follows(AnnotatedSegment previous) {
        return previous != null && previous.trackId == trackId && previous.seqNo + 1 == seqNo;
    }
}
