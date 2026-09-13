package dev.rifflab.harmony;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Pertinência ao campo harmônico: aritmética de escalas, não julgamento de análise. */
class KeyTest {

    private static final Key C_MAJOR = new Key(0, KeyMode.MAJOR);
    private static final Key C_MINOR = new Key(0, KeyMode.MINOR);
    private static final Key C_AEOLIAN = new Key(0, KeyMode.AEOLIAN);

    @ParameterizedTest(name = "C major: {0} {1} diatonic={2}")
    @CsvSource({
            "0, MAJ, true", "2, MIN, true", "4, MIN, true", "5, MAJ, true", "7, MAJ, true", "9, MIN, true",
            "11, DIM, true", "7, DOM7, true", "5, MAJ7, true", "2, MIN7, true", "11, HDIM7, true",
            "0, DOM7, false", "10, MAJ, false", "8, MAJ, false", "4, MAJ, false", "0, MIN, false",
    })
    void diatonicInMajor(int root, ChordQuality quality, boolean expected) {
        assertThat(C_MAJOR.isDiatonic(Chord.of(root, quality))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "C minor: {0} {1} diatonic={2}")
    @CsvSource({
            "0, MIN, true", "3, MAJ, true", "5, MIN, true", "8, MAJ, true", "10, MAJ, true", "2, DIM, true",
            "7, MIN, true",                     // v natural
            "7, MAJ, true", "7, DOM7, true",    // V da harmônica: função dominante conta
            "11, DIM, true", "11, DIM7, true",  // vii° e vii°7 da harmônica
            "11, MAJ, false",                   // VII maior com D#: nem natural nem harmônica
            "5, MAJ, false", "0, MAJ, false", "2, MIN, false",
    })
    void diatonicInTonalMinorIncludesHarmonicDominant(int root, ChordQuality quality, boolean expected) {
        assertThat(C_MINOR.isDiatonic(Chord.of(root, quality))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "C aeolian (strict): {0} {1} diatonic={2}")
    @CsvSource({"7, MIN, true", "7, MAJ, false", "11, DIM, false"})
    void aeolianIsStrict(int root, ChordQuality quality, boolean expected) {
        assertThat(C_AEOLIAN.isDiatonic(Chord.of(root, quality))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "borrowed into C major: {0} {1} = {2}")
    @CsvSource({
            "8, MAJ, true", "3, MAJ, true", "10, MAJ, true", "5, MIN, true", "2, DIM, true",
            "1, MAJ, false",   // ♭II frígio não está na menor natural
            "4, MAJ, false",   // III maior
            "0, MAJ, false",   // o próprio I: E não está em C menor natural
    })
    void borrowedFromParallelMinor(int root, ChordQuality quality, boolean expected) {
        assertThat(C_MAJOR.isBorrowed(Chord.of(root, quality))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "borrowed into C minor: {0} {1} = {2}")
    @CsvSource({"5, MAJ, true", "2, MIN, true", "9, MIN, true", "0, MAJ, true", "1, MAJ, false", "8, MAJ, false"})
    void borrowedFromParallelMajor(int root, ChordQuality quality, boolean expected) {
        assertThat(C_MINOR.isBorrowed(Chord.of(root, quality))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"MAJOR, MINOR", "LYDIAN, MINOR", "MIXOLYDIAN, MINOR", "MINOR, MAJOR", "DORIAN, MAJOR",
            "PHRYGIAN, MAJOR", "AEOLIAN, MAJOR", "LOCRIAN, MAJOR"})
    void parallelFollowsTheThird(KeyMode mode, KeyMode parallel) {
        assertThat(mode.parallel()).isEqualTo(parallel);
    }

    @ParameterizedTest
    @CsvSource({"9, 0, 3", "9, 9, 0", "4, 2, 10", "7, 5, 10"})
    void degreeIntervalIsRootMinusTonicMod12(int tonic, int root, int expected) {
        assertThat(new Key(tonic, KeyMode.MAJOR).degreeInterval(root)).isEqualTo(expected);
    }
}
