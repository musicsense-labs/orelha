package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.harmony.KeyMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceTextTest {

    @Test
    void keyLineAndSections() {
        ReferenceText.Parsed p = ReferenceText.parse("""
                tonalidade: G major
                Intro: I III IV iv
                Verse: I III IV iv
                Chorus: I III
                IV iv
                """);
        assertThat(p.tonicPc()).isEqualTo(7);
        assertThat(p.mode()).isEqualTo(KeyMode.MAJOR);
        assertThat(p.sections()).hasSize(3);
        assertThat(p.sections().get(2).label()).isEqualTo("Chorus");
        assertThat(p.sections().get(2).progression()).isEqualTo("I III IV iv");   // linha sem rótulo continua a anterior
    }

    @Test
    void keyInPortugueseAndMinor() {
        assertThat(ReferenceText.parse("tom: Sol menor\nVerso: i VI VII").tonicPc()).isEqualTo(7);
        assertThat(ReferenceText.parse("tom: Sol menor\nVerso: i VI VII").mode()).isEqualTo(KeyMode.MINOR);
        assertThat(ReferenceText.parse("key: Eb\nA: I").tonicPc()).isEqualTo(3);
        assertThat(ReferenceText.parse("key: F# minor\nA: i").tonicPc()).isEqualTo(6);
        assertThat(ReferenceText.parse("key: E mixolydian\nA: I bVII").mode()).isEqualTo(KeyMode.MIXOLYDIAN);
    }

    @Test
    void textWithoutKeyOrLabels() {
        ReferenceText.Parsed p = ReferenceText.parse("I V vi IV");
        assertThat(p.tonicPc()).isNull();
        assertThat(p.sections()).hasSize(1);
        assertThat(p.sections().get(0).label()).isEqualTo("?");
    }
}
