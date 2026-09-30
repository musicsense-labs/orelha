package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.lyrics.LyricMerger;
import dev.musicsense.orelha.lyrics.LyricMerger.Segment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VocalNoteClassifierTest {

    private final VocalNoteClassifier classifier = new VocalNoteClassifier(0.12, 0.3);

    private static VocalNote note(double start, double end) {
        return new VocalNote(null, BigDecimal.valueOf(start), BigDecimal.valueOf(end), 60, 90);
    }

    /** Cada palavra: {início, fim} (probabilidade 0,9 do ASR) ou {início, fim, probabilidade}; NaN = sem probabilidade. */
    private static Segment segment(double start, double end, double[]... words) {
        List<LyricMerger.Word> list = new ArrayList<>();
        for (double[] w : words) {
            Double p = Double.valueOf(0.9);
            if (w.length > 2) {
                p = Double.isNaN(w[2]) ? null : Double.valueOf(w[2]);   // sem ternário: ele desembrulharia o null
            }
            list.add(new LyricMerger.Word(w[0], w[1], "w", p));
        }
        return new Segment(start, end, "x", list);
    }

    @Test
    void withoutLyricsEveryNoteIsLexical() {
        assertThat(classifier.kindOf(note(0, 1), List.of())).isEqualTo(VocalNoteKind.LEXICAL);
    }

    @Test
    void noteUnderAWordIsLexical() {
        Segment seg = segment(0, 4, new double[]{1.0, 1.5});
        assertThat(classifier.kindOf(note(1.1, 1.4), List.of(seg))).isEqualTo(VocalNoteKind.LEXICAL);
        // Folga de 120 ms: o basic-pitch ataca um pouco antes da consoante.
        assertThat(classifier.kindOf(note(0.9, 1.0), List.of(seg))).isEqualTo(VocalNoteKind.LEXICAL);
        assertThat(classifier.kindOf(note(0.5, 0.8), List.of(seg))).isEqualTo(VocalNoteKind.NON_LEXICAL);
    }

    @Test
    void aWordTheLrcBroughtCountsEvenWithoutProbability() {
        // O caso que motivou ler a letra fundida: o ASR não ouviu "was", o .lrc trouxe (sem probabilidade), e a
        // nota sob ela era escondida como vazamento no piano roll enquanto a letra aparecia na lane.
        Segment seg = segment(49.8, 52, new double[]{49.8, 50.1}, new double[]{50.6, 50.9, Double.NaN});
        assertThat(classifier.kindOf(note(50.6, 50.9), List.of(seg))).isEqualTo(VocalNoteKind.LEXICAL);
    }

    @Test
    void lowProbabilityWordsDoNotCount() {
        // O trecho é crível pela segunda palavra; a nota sob a palavra de 0,1 fica sem texto.
        Segment seg = segment(0, 4, new double[]{1.0, 1.5, 0.1}, new double[]{3.0, 3.5});
        assertThat(classifier.kindOf(note(1.1, 1.4), List.of(seg))).isEqualTo(VocalNoteKind.NON_LEXICAL);
    }

    @Test
    void noteInsideASegmentWithoutWordIsNonLexical() {
        Segment seg = segment(0, 4, new double[]{0.5, 0.8});
        assertThat(classifier.kindOf(note(2, 3), List.of(seg))).isEqualTo(VocalNoteKind.NON_LEXICAL);
    }

    @Test
    void noteOutsideAnySegmentIsLikelyLeak() {
        Segment seg = segment(0, 4, new double[]{0.5, 0.8});
        assertThat(classifier.kindOf(note(10, 11), List.of(seg))).isEqualTo(VocalNoteKind.LIKELY_LEAK);
    }

    @Test
    void segmentWhoseWordsAreAllImplausibleIsAHallucinationAndDoesNotCount() {
        Segment seg = segment(0, 4, new double[]{1.0, 1.5, 0.06});
        assertThat(classifier.kindOf(note(1.1, 1.4), List.of(seg))).isEqualTo(VocalNoteKind.LIKELY_LEAK);
        assertThat(classifier.kindOf(note(2.0, 3.0), List.of(seg))).isEqualTo(VocalNoteKind.LIKELY_LEAK);
    }
}
