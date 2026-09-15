package dev.musicsense.orelha.harmony;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BassRoleTest {

    @ParameterizedTest(name = "{0}{1}/{2} = {3}")
    @CsvSource({
            "0, MAJ, 0, ROOT", "0, MAJ, 4, THIRD", "0, MIN, 3, THIRD", "0, MAJ, 7, FIFTH", "0, DIM, 6, FIFTH",
            "0, AUG, 8, FIFTH", "0, DOM7, 10, SEVENTH", "0, MAJ7, 11, SEVENTH", "0, MAJ6, 9, SEVENTH",
            "0, SUS4, 5, SUSPENDED", "0, SUS2, 2, SUSPENDED",
            "0, MAJ, 2, NON_CHORD_TONE", "0, MAJ, 10, NON_CHORD_TONE", "0, POWER, 4, NON_CHORD_TONE",
    })
    void roleOfBass(int root, ChordQuality quality, int bass, BassRole expected) {
        assertThat(BassRole.of(new Chord(root, quality, bass))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0, MAJ", "0, POWER"})
    void unknownWithoutBass(int root, ChordQuality quality) {
        assertThat(BassRole.of(Chord.of(root, quality))).isEqualTo(BassRole.UNKNOWN);
        assertThat(BassRole.of(Chord.noChord())).isEqualTo(BassRole.UNKNOWN);
    }
}
