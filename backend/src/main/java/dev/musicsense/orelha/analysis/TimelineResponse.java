package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.harmony.ChordQuality;
import dev.musicsense.orelha.harmony.KeyMode;

import java.math.BigDecimal;
import java.util.List;

/** Segmentos de um run com grau e classe funcional — o que a UI desenha. */
public record TimelineResponse(Long trackId, Long runId, String normalizerVersion, KeyInfo key, BigDecimal bpm,
                               String timeSignature, List<Segment> segments) {

    public record KeyInfo(int tonicPc, KeyMode mode, Float confidence, KeySource source) {
    }

    public record Segment(int seqNo, BigDecimal startS, BigDecimal endS, Integer rootPc, ChordQuality quality,
                          Integer bassPc, Float confidence, Integer degreeInterval, String degreeLabel,
                          String keyRelation, String relationFromPrev, boolean inverted, Integer effectiveBassPc) {

        static Segment of(HarmonicAnnotation a) {
            ChordSegment s = a.getSegment();
            return new Segment(s.getSeqNo(), s.getStartS(), s.getEndS(), s.getRootPc(), s.getQuality(), s.getBassPc(),
                    s.getConfidence(), a.getDegreeInterval(), a.getDegreeLabel(), a.getFunctionClass(),
                    a.getRelationFromPrev(), a.isInverted(), a.getEffectiveBassPc());
        }
    }
}
