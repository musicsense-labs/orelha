package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.harmony.ChordQuality;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Partes do run com a progressão de um ciclo cada — o resumo harmônico que a UI desenha por parte. */
public record SectionsResponse(Long trackId, Long runId, SectionSource source, List<Part> parts) {

    public record Part(Long id, String label, BigDecimal startS, BigDecimal endS, BigDecimal cycleEndS, int repeats,
                       List<Chord> chords) {

        static Part of(Section s, List<Chord> chords) {
            return new Part(s.getId(), s.getLabel(), s.getStartS(), s.getEndS(), s.getCycleEndS(), s.getRepeats(), chords);
        }
    }

    public record Chord(BigDecimal startS, BigDecimal endS, Integer rootPc, ChordQuality quality, Integer bassPc,
                        String degreeLabel, String keyRelation) {

        static Chord of(HarmonicAnnotation a) {
            ChordSegment s = a.getSegment();
            return new Chord(s.getStartS(), s.getEndS(), s.getRootPc(), s.getQuality(), s.getBassPc(),
                    a.getDegreeLabel(), a.getFunctionClass());
        }

        Chord clipped(BigDecimal start, BigDecimal end) {
            return new Chord(start, end, rootPc, quality, bassPc, degreeLabel, keyRelation);
        }

        boolean sameChord(Chord other) {
            return Objects.equals(rootPc, other.rootPc) && quality == other.quality && Objects.equals(bassPc, other.bassPc);
        }
    }
}
