package dev.musicsense.orelha.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VocalNoteClassifierTest {

    private final VocalNoteClassifier classifier = new VocalNoteClassifier(0.6, 0.12, 0.3);

    private static BigDecimal s(double v) {
        return BigDecimal.valueOf(v);
    }

    private static VocalNote note(double start, double end) {
        return new VocalNote(null, s(start), s(end), 60, 90);
    }

    private static LyricSegment segment(double start, double end, float noSpeech, double[]... words) {
        LyricSegment segment = new LyricSegment(null, s(start), s(end), "x", noSpeech);
        for (double[] w : words) {
            segment.addWord(s(w[0]), s(w[1]), "w", w.length > 2 ? (float) w[2] : 0.9f);
        }
        return segment;
    }

    @Test
    void withoutLyricsEveryNoteIsLexical() {
        assertThat(classifier.kindOf(note(0, 1), List.of())).isEqualTo(VocalNoteKind.LEXICAL);
    }

    @Test
    void noteUnderAWordIsLexical() {
        LyricSegment seg = segment(0, 4, 0.1f, new double[]{1.0, 1.5});
        assertThat(classifier.kindOf(note(1.1, 1.4), List.of(seg))).isEqualTo(VocalNoteKind.LEXICAL);
        // Folga de 120 ms: o basic-pitch ataca um pouco antes da consoante.
        assertThat(classifier.kindOf(note(0.9, 1.0), List.of(seg))).isEqualTo(VocalNoteKind.LEXICAL);
        assertThat(classifier.kindOf(note(0.5, 0.8), List.of(seg))).isEqualTo(VocalNoteKind.NON_LEXICAL);
    }

    @Test
    void lowProbabilityWordsDoNotCount() {
        LyricSegment seg = segment(0, 4, 0.1f, new double[]{1.0, 1.5, 0.1});
        assertThat(classifier.kindOf(note(1.1, 1.4), List.of(seg))).isEqualTo(VocalNoteKind.NON_LEXICAL);
    }

    @Test
    void noteInsideASpeechSegmentWithoutWordIsNonLexical() {
        LyricSegment seg = segment(0, 4, 0.2f);
        assertThat(classifier.kindOf(note(2, 3), List.of(seg))).isEqualTo(VocalNoteKind.NON_LEXICAL);
    }

    @Test
    void noteInANoSpeechSegmentOrOutsideAnySegmentIsLikelyLeak() {
        LyricSegment noSpeech = segment(0, 4, 0.9f);
        assertThat(classifier.kindOf(note(2, 3), List.of(noSpeech))).isEqualTo(VocalNoteKind.LIKELY_LEAK);
        assertThat(classifier.kindOf(note(10, 11), List.of(noSpeech))).isEqualTo(VocalNoteKind.LIKELY_LEAK);
    }

    @Test
    void aWordInsideANoSpeechSegmentStillWins() {
        // O ASR alinhou uma palavra confiável mesmo achando o trecho pouco "fala": a palavra é a evidência mais forte.
        LyricSegment seg = segment(0, 4, 0.8f, new double[]{1.0, 1.5});
        assertThat(classifier.kindOf(note(1.0, 1.5), List.of(seg))).isEqualTo(VocalNoteKind.LEXICAL);
    }
}
