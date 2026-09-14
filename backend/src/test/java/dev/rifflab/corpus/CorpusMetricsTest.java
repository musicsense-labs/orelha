package dev.rifflab.corpus;

import dev.rifflab.corpus.CorpusMetrics.DegreeDistribution;
import dev.rifflab.corpus.CorpusMetrics.PedalPassage;
import dev.rifflab.corpus.CorpusMetrics.Share;
import dev.rifflab.corpus.CorpusMetrics.TransitionMatrix;
import dev.rifflab.harmony.ChordQuality;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CorpusMetricsTest {

    /** Sequência sintética numa faixa: I (2 s) → ♭VI (1 s, mediante cromático, baixo parado) → V (1 s) → N (1 s). */
    private static AnnotatedSegment seg(long track, int seq, double start, double end, Integer degree, String label,
                                        String relation, String fromPrev, Integer bass) {
        return new AnnotatedSegment(track, "t" + track, seq, start, end, degree == null ? null : degree,
                degree == null ? ChordQuality.NO_CHORD : ChordQuality.MAJ, degree, label, relation, fromPrev, bass);
    }

    private static final List<AnnotatedSegment> ONE_TRACK = List.of(
            seg(1, 0, 0, 2, 0, "I", "DIATONIC", null, 0),
            seg(1, 1, 2, 3, 8, "♭VI", "BORROWED", "CHROMATIC_MEDIANT", 0),
            seg(1, 2, 3, 4, 7, "V", "DIATONIC", "SEMITONE", 7),
            seg(1, 3, 4, 5, null, null, "NONE", null, null));

    @Test
    void sharesInBothUnits() {
        Map<String, Share> shares = CorpusMetrics.keyRelationShares(ONE_TRACK);
        assertThat(shares.get("DIATONIC").bySegment()).isEqualTo(0.5);      // 2 de 4 segmentos
        assertThat(shares.get("DIATONIC").byDuration()).isEqualTo(0.6);     // 3 s de 5 s
        assertThat(shares.get("BORROWED").byDuration()).isEqualTo(0.2);

        Share nonDiatonic = CorpusMetrics.nonDiatonic(ONE_TRACK);            // N não conta; ♭VI é 1 de 3, 1 s de 4 s
        assertThat(nonDiatonic.bySegment()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(nonDiatonic.byDuration()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void ambiguousCountsAsInsideTheField() {
        List<AnnotatedSegment> segments = List.of(
                seg(1, 0, 0, 1, 0, "I5", "AMBIGUOUS", null, 0),
                seg(1, 1, 1, 2, 1, "♭II", "CHROMATIC", "SEMITONE", 1));
        assertThat(CorpusMetrics.nonDiatonic(segments).bySegment()).isEqualTo(0.5);
    }

    @Test
    void degreeDistributionAndEntropy() {
        DegreeDistribution d = CorpusMetrics.degrees(ONE_TRACK);
        assertThat(d.bySegment()[0]).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(d.byDuration()[0]).isEqualTo(0.5);
        assertThat(d.entropyBySegmentBits()).isCloseTo(Math.log(3) / Math.log(2), within(1e-9)); // 3 graus equiprováveis
        assertThat(CorpusMetrics.entropyBits(new double[]{1, 0, 0})).isZero();
        assertThat(CorpusMetrics.entropyBits(new double[]{0.5, 0.5})).isEqualTo(1.0);
    }

    @Test
    void transitionsOnlyBetweenConsecutiveSegmentsOfTheSameTrack() {
        List<AnnotatedSegment> twoTracks = new java.util.ArrayList<>(ONE_TRACK);
        twoTracks.add(seg(2, 0, 0, 1, 7, "V", "DIATONIC", null, 7));            // outra faixa: não é transição de N
        twoTracks.add(seg(2, 1, 1, 2, 0, "I", "DIATONIC", "FIFTH_DOWN", 0));
        TransitionMatrix m = CorpusMetrics.transitions(twoTracks);
        assertThat(m.total()).isEqualTo(3);                                    // I→♭VI, ♭VI→V, V→I
        assertThat(m.counts()[0][8]).isEqualTo(1);
        assertThat(m.rowNormalized()[7][0]).isEqualTo(1.0);                    // de V só se foi para I
        assertThat(m.flattened()).hasSize(144);
        assertThat(java.util.Arrays.stream(m.flattened()).sum()).isCloseTo(1.0, within(1e-9));

        Map<String, Share> relations = CorpusMetrics.relationShares(twoTracks);
        assertThat(relations.get("CHROMATIC_MEDIANT").bySegment()).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    void pedalPassagesNeedTheSameBassOnBothSides() {
        List<PedalPassage> pedals = CorpusMetrics.pedalPassages(ONE_TRACK, "CHROMATIC_MEDIANT");
        assertThat(pedals).hasSize(1);
        assertThat(pedals.get(0).fromLabel()).isEqualTo("I");
        assertThat(pedals.get(0).toLabel()).isEqualTo("♭VI");
        assertThat(pedals.get(0).bassPc()).isZero();
        assertThat(pedals.get(0).startS()).isZero();
        assertThat(pedals.get(0).endS()).isEqualTo(3.0);
        assertThat(CorpusMetrics.pedalPassages(ONE_TRACK, "SEMITONE")).isEmpty();   // ♭VI→V muda o baixo
    }

    @Test
    void distances() {
        double[] p = {0.5, 0.5, 0, 0};
        double[] q = {0, 0, 0.5, 0.5};
        assertThat(CorpusMetrics.jensenShannonBits(p, p)).isZero();
        assertThat(CorpusMetrics.jensenShannonBits(p, q)).isCloseTo(1.0, within(1e-9));   // suportes disjuntos
        assertThat(CorpusMetrics.jensenShannonBits(p, q)).isEqualTo(CorpusMetrics.jensenShannonBits(q, p));
        assertThat(CorpusMetrics.l1(p, q)).isEqualTo(2.0);
        assertThat(CorpusMetrics.l1(Map.of("A", new Share(1, 1)), Map.of("B", new Share(1, 1)))).isEqualTo(2.0);
        assertThat(CorpusMetrics.l1(Map.of("A", new Share(0.5, 0)), Map.of("A", new Share(0.25, 0)))).isEqualTo(0.25);
    }
}
