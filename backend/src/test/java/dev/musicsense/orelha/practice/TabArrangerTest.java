package dev.musicsense.orelha.practice;

import dev.musicsense.orelha.practice.TabArranger.Note;
import dev.musicsense.orelha.practice.TabArranger.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TabArrangerTest {

    private static List<Note> line(double step, int... midis) {
        return IntStream.range(0, midis.length).mapToObj(i -> new Note(i * step, midis[i])).toList();
    }

    @Test
    void openStringsWhenPreferredAndReachable() {
        // E1 A1 D2 G2 são as quatro soltas; com preferência, ficam soltas.
        List<Position> tab = TabArranger.standard4(true).arrange(line(0.5, 28, 33, 38, 43));
        assertThat(tab).extracting(Position::string).containsExactly(0, 1, 2, 3);
        assertThat(tab).extracting(Position::fret).containsExactly(0, 0, 0, 0);
    }

    @Test
    void withoutOpenPreferenceTheSamePitchesAreFretted() {
        List<Position> tab = TabArranger.standard4(false).arrange(line(0.5, 33, 38, 43));
        // A1 na corda E casa 5, D2 na A casa 5, G2 na D casa 5 — tudo na 5ª posição.
        assertThat(tab).extracting(Position::fret).containsExactly(5, 5, 5);
        assertThat(tab).extracting(Position::string).containsExactly(0, 1, 2);
    }

    @Test
    void aFastLineStaysInOnePositionInsteadOfJumpingBetweenStrings() {
        // Lá2 Si2 Dó3 Si2 Lá2 (45 47 48 47 45) rápido: cabe na corda G casas 2–5 (ou D 7–10); não vai a
        // E 17ª / A 12ª e não muda de corda a cada nota.
        List<Position> tab = TabArranger.standard4(true).arrange(line(0.2, 45, 47, 48, 47, 45));
        assertThat(tab).extracting(Position::string).containsOnly(tab.get(0).string());
        assertThat(tab).allSatisfy(p -> assertThat(p.fret()).isLessThanOrEqualTo(10));
    }

    @Test
    void handSpanBeatsStringChange() {
        // Mi2 (40) depois de Ré2 na corda D casa 0... com preferência por solta: D solta, depois E na D casa 2
        // (mesma corda, 2 casas) e não na A casa 7.
        List<Position> tab = TabArranger.standard4(true).arrange(line(0.25, 38, 40, 38, 40));
        assertThat(tab).extracting(Position::string).containsExactly(2, 2, 2, 2);
        assertThat(tab).extracting(Position::fret).containsExactly(0, 2, 0, 2);
    }

    @Test
    void notesBelowTheNeckAreRaisedAnOctaveAndFlagged() {
        // Ré1 (26) não existe num 4 cordas afinado em Mi: vira Ré2 e sai marcado.
        List<Position> tab = TabArranger.standard4(true).arrange(line(0.5, 26, 28));
        assertThat(tab.get(0).octaveShifted()).isTrue();
        assertThat(tab.get(0).fret()).isIn(0, 5, 10);   // Ré2 em D solta, A 5ª ou E 10ª
        assertThat(tab.get(1)).isEqualTo(new Position(0, 0, false));
    }

    @Test
    void emptyLineGivesEmptyTab() {
        assertThat(TabArranger.standard4(true).arrange(List.of())).isEmpty();
    }
}
