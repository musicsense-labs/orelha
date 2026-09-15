package dev.musicsense.orelha.harmony;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.musicsense.orelha.harmony.ChordQuality.MAJ;
import static dev.musicsense.orelha.harmony.ChordQuality.MIN;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Só a fiação do normalizer (índices, grau, transição do primeiro acorde, NO_CHORD).
 * A classificação em KeyRelation é validada pelos testes parametrizados com as progressões
 * fornecidas pelo dono do projeto — ver {@code ProgressionClassificationTest} (a chegar).
 */
class HarmonicNormalizerTest {

    private final HarmonicNormalizer normalizer = new HarmonicNormalizer();

    @Test
    void wiresDegreeLabelAndTransitions() {
        Key aMinor = new Key(9, KeyMode.MINOR);
        List<NormalizedChord> out = normalizer.normalize(
                List.of(Chord.of(9, MIN), Chord.noChord(), Chord.of(5, MAJ), Chord.of(7, MAJ)), aMinor);

        assertThat(out).extracting(NormalizedChord::index).containsExactly(0, 1, 2, 3);
        assertThat(out).extracting(NormalizedChord::degreeInterval).containsExactly(0, null, 8, 10);
        assertThat(out).extracting(NormalizedChord::degreeLabel).containsExactly("i", null, "♭VI", "♭VII");
        assertThat(out.get(1).keyRelation()).isEqualTo(KeyRelation.NONE);

        assertThat(out.get(0).fromPrevious()).isNull();                 // primeiro acorde
        assertThat(out.get(2).fromPrevious()).isNull();                 // anterior é NO_CHORD
        assertThat(out.get(3).fromPrevious().relation()).isEqualTo(ChordRelation.WHOLE_TONE);
        assertThat(out.get(3).fromPrevious().rootInterval()).isEqualTo(2);
    }

    @Test
    void emptySequenceIsEmpty() {
        assertThat(normalizer.normalize(List.of(), new Key(0, KeyMode.MAJOR))).isEmpty();
    }
}
