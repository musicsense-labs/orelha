package dev.rifflab.harmony;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PowerChordDetectorTest {

    private final PowerChordDetector detector = new PowerChordDetector(PowerChordDetector.DEFAULT_THIRD_RATIO);

    private static float[] chroma(int... weightedPcs) {
        // pares pc, peso em décimos: chroma(4, 10, 11, 8) = E:1.0, B:0.8
        float[] c = new float[12];
        for (int i = 0; i < weightedPcs.length; i += 2) {
            c[weightedPcs[i]] = weightedPcs[i + 1] / 10f;
        }
        return c;
    }

    @Test
    void dyadWithoutThirdIsPower() {
        float[] e5 = chroma(4, 10, 11, 8, 8, 1, 7, 1);       // E, B fortes; G#/G residuais
        assertThat(detector.isPowerChord(e5, 4)).isTrue();
        assertThat(detector.reclassify(Chord.of(4, ChordQuality.MAJ), e5).quality()).isEqualTo(ChordQuality.POWER);
        assertThat(detector.reclassify(Chord.of(4, ChordQuality.MIN), e5).quality()).isEqualTo(ChordQuality.POWER);
    }

    @Test
    void audibleThirdStaysTriad() {
        float[] eMajor = chroma(4, 10, 8, 7, 11, 8);         // E, G#, B
        assertThat(detector.isPowerChord(eMajor, 4)).isFalse();
        assertThat(detector.reclassify(Chord.of(4, ChordQuality.MAJ), eMajor).quality()).isEqualTo(ChordQuality.MAJ);
        float[] eMinor = chroma(4, 10, 7, 6, 11, 8);         // E, G, B
        assertThat(detector.isPowerChord(eMinor, 4)).isFalse();
    }

    @Test
    void onlyMajorAndMinorTriadsAreReclassified() {
        float[] e5 = chroma(4, 10, 11, 8);
        assertThat(detector.reclassify(Chord.of(4, ChordQuality.DOM7), e5).quality()).isEqualTo(ChordQuality.DOM7);
        assertThat(detector.reclassify(Chord.of(4, ChordQuality.SUS4), e5).quality()).isEqualTo(ChordQuality.SUS4);
    }

    @Test
    void missingChromaNeverReclassifies() {
        assertThat(detector.isPowerChord(null, 4)).isFalse();
        assertThat(detector.isPowerChord(new float[12], 4)).isFalse();   // silêncio: sem referência
        assertThat(detector.reclassify(Chord.of(4, ChordQuality.MAJ), null).quality()).isEqualTo(ChordQuality.MAJ);
    }
}
