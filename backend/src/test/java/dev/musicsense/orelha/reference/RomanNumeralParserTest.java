package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.reference.RomanNumeralParser.Degree;
import dev.musicsense.orelha.harmony.KeyMode;
import dev.musicsense.orelha.reference.RomanNumeralParser.Family;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RomanNumeralParserTest {

    @Test
    void diatonicTriadsByCase() {
        assertThat(RomanNumeralParser.parse("I ii iii IV V vi vii°")).extracting(Degree::key)
                .containsExactly("0:MAJOR", "2:MINOR", "4:MINOR", "5:MAJOR", "7:MAJOR", "9:MINOR", "11:DIMINISHED");
    }

    @Test
    void accidentalsAndSuffixes() {
        assertThat(RomanNumeralParser.parse("bVII ♭VI #iv° V7 Isus4 IV6 I64 iv")).extracting(Degree::key)
                .containsExactly("10:MAJOR", "8:MAJOR", "6:DIMINISHED", "7:MAJOR", "0:MAJOR", "5:MAJOR", "0:MAJOR", "5:MINOR");
    }

    @Test
    void appliedDominantIsTheMajorTriadAFifthAboveItsTarget() {
        List<Degree> parsed = RomanNumeralParser.parse("V/vi V7/V V/ii");
        assertThat(parsed).extracting(Degree::key).containsExactly("4:MAJOR", "2:MAJOR", "9:MAJOR");   // E, D, A em Dó
        assertThat(parsed).allMatch(Degree::applied);
    }

    @Test
    void separatorsAndGarbage() {
        assertThat(RomanNumeralParser.parse("I - V - vi - IV, then ??? | ii→V")).extracting(Degree::key)
                .containsExactly("0:MAJOR", "7:MAJOR", "9:MINOR", "5:MAJOR", "2:MINOR", "7:MAJOR");
        assertThat(RomanNumeralParser.parse(null)).isEmpty();
    }

    @Test
    void degreesFollowTheDeclaredMode() {
        // TheoryTab em Fá menor: III e VI são Lá♭ e Ré♭ (3 e 8), não Lá e Ré.
        assertThat(RomanNumeralParser.parse("i iv III VI", KeyMode.MINOR)).extracting(Degree::key)
                .containsExactly("0:MINOR", "5:MINOR", "3:MAJOR", "8:MAJOR");
        assertThat(RomanNumeralParser.parse("i IV v III", KeyMode.DORIAN)).extracting(Degree::key)
                .containsExactly("0:MINOR", "5:MAJOR", "7:MINOR", "3:MAJOR");
        assertThat(RomanNumeralParser.parse("ii VII V I", KeyMode.MIXOLYDIAN)).extracting(Degree::key)
                .containsExactly("2:MINOR", "10:MAJOR", "7:MAJOR", "0:MAJOR");
        // #vii em menor = sensível (11); V/x continua sendo a 5ª justa acima do alvo, em qualquer modo.
        assertThat(RomanNumeralParser.parse("#vii V/iv", KeyMode.MINOR)).extracting(Degree::key)
                .containsExactly("11:MINOR", "0:MAJOR");
    }

    @Test
    void powerChordsReduceToMajor() {
        assertThat(RomanNumeralParser.parse("i(no3) iv(no3) I5", KeyMode.MINOR)).extracting(Degree::key)
                .containsExactly("0:MAJOR", "5:MAJOR", "0:MAJOR");
    }

    @Test
    void inversionByNumberAfterSlashKeepsTheHead() {
        assertThat(RomanNumeralParser.parseOne("I/3").family()).isEqualTo(Family.MAJOR);
        assertThat(RomanNumeralParser.parseOne("I/3").degreeInterval()).isZero();
    }
}
