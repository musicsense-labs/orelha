package dev.rifflab.harmony;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RomanNumeralTest {

    @ParameterizedTest(name = "interval {0} {1} = {2}")
    @CsvSource({
            "0, MAJ, I", "2, MIN, ii", "4, MIN, iii", "5, MAJ, IV", "7, MAJ, V", "9, MIN, vi", "11, DIM, vii°",
            "3, MAJ, ♭III", "8, MAJ, ♭VI", "10, MAJ, ♭VII", "1, MAJ, ♭II", "6, DIM, ♯iv°",
            "7, DOM7, V7", "2, MIN7, ii7", "0, MAJ7, Imaj7", "2, HDIM7, iiø7", "11, DIM7, vii°7", "4, AUG, III+",
            "0, MINMAJ7, imM7", "9, MIN6, vi6", "5, MAJ6, IV6",
            "8, POWER, ♭VI5", "0, POWER, I5", "5, SUS4, IVsus4", "7, SUS2, Vsus2",
    })
    void rendersRelativeToMajorScale(int interval, ChordQuality quality, String expected) {
        assertThat(RomanNumeral.label(interval, quality)).isEqualTo(expected);
    }
}
