package dev.rifflab.extraction;

import dev.rifflab.harmony.Chord;
import dev.rifflab.harmony.ChordQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HarteLabelTest {

    @ParameterizedTest(name = "{0} = {1} {2}")
    @CsvSource({
            "C, 0, MAJ", "C:maj, 0, MAJ", "C#:min7, 1, MIN7", "Db:maj7, 1, MAJ7", "Bb:7, 10, DOM7",
            "F#:hdim7, 6, HDIM7", "G:minmaj7, 7, MINMAJ7", "A:sus4, 9, SUS4", "E:dim7, 4, DIM7",
            "Cb, 11, MAJ", "B#:min, 0, MIN", "G:maj/3, 7, MAJ",
    })
    void parsesRootAndQuality(String label, int rootPc, ChordQuality quality) {
        Chord chord = HarteLabel.parse(label);
        assertThat(chord.rootPc()).isEqualTo(rootPc);
        assertThat(chord.quality()).isEqualTo(quality);
        assertThat(chord.bassPc()).isNull();
    }

    @Test
    void parsesNoChordAndUnknown() {
        assertThat(HarteLabel.parse("N")).isEqualTo(Chord.noChord());
        assertThat(HarteLabel.parse("X").quality()).isEqualTo(ChordQuality.UNKNOWN);
        assertThat(HarteLabel.parse("X").rootPc()).isNull();
    }

    @Test
    void rejectsLabelsOutsideTheVocabulary() {
        assertThatThrownBy(() -> HarteLabel.parse("C:9")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HarteLabel.parse("H:maj")).isInstanceOf(IllegalArgumentException.class);
    }
}
