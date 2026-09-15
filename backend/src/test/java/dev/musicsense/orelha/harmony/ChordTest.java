package dev.musicsense.orelha.harmony;

import org.junit.jupiter.api.Test;

import static dev.musicsense.orelha.harmony.ChordQuality.DOM7;
import static dev.musicsense.orelha.harmony.ChordQuality.MIN7;
import static dev.musicsense.orelha.harmony.ChordQuality.NO_CHORD;
import static dev.musicsense.orelha.harmony.ChordQuality.POWER;
import static dev.musicsense.orelha.harmony.PitchClasses.mask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChordTest {

    private static final int C = 0, E = 4, G = 7, BB = 10, EB = 3, D = 2;

    @Test
    void pitchClassesIncludeExtensions() {
        assertThat(Chord.of(C, DOM7).pitchClasses()).isEqualTo(mask(C, E, G, BB));
        assertThat(Chord.of(C, MIN7).pitchClasses()).isEqualTo(mask(C, EB, G, BB));
        assertThat(Chord.of(E, POWER).pitchClasses()).isEqualTo(mask(E, 11));
    }

    @Test
    void triadDropsSeventhAndIsEmptyWithoutThird() {
        assertThat(Chord.of(C, MIN7).triad()).isEqualTo(mask(C, EB, G));
        assertThat(Chord.of(C, POWER).triad()).isZero();
        assertThat(Chord.noChord().triad()).isZero();
    }

    @Test
    void inversionRequiresBassDifferentFromRoot() {
        assertThat(new Chord(C, DOM7, E).isInverted()).isTrue();
        assertThat(new Chord(C, DOM7, C).isInverted()).isFalse();
        assertThat(Chord.of(C, DOM7).isInverted()).isFalse();
    }

    @Test
    void rootPresenceMustMatchQuality() {
        assertThatThrownBy(() -> new Chord(C, NO_CHORD, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chord(null, DOM7, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Chord.of(12, DOM7)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transposeWrapsAround() {
        assertThat(PitchClasses.transpose(mask(C, E, G), 10)).isEqualTo(mask(BB, D, 5));
        assertThat(PitchClasses.interval(G, C)).isEqualTo(5);
        assertThat(PitchClasses.interval(C, G)).isEqualTo(7);
    }
}
