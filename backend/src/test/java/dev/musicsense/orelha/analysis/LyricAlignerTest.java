package dev.musicsense.orelha.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LyricAlignerTest {

    private static VocalNote note(double start, int midi) {
        return new VocalNote(null, BigDecimal.valueOf(start), BigDecimal.valueOf(start + 0.3), midi, 90);
    }

    private final List<VocalNote> notes = List.of(note(1.0, 60), note(1.5, 62), note(2.0, 64));

    @Test
    void snapsToTheNearestOnsetWithinTolerance() {
        LyricAligner.Onset onset = LyricAligner.nearestOnset(BigDecimal.valueOf(1.42), notes, 0.12);
        assertThat(onset.startS()).isEqualByComparingTo("1.5");
        assertThat(onset.midi()).isEqualTo(62);
    }

    @Test
    void nothingWhenNoOnsetIsCloseEnough() {
        assertThat(LyricAligner.nearestOnset(BigDecimal.valueOf(1.25), notes, 0.12)).isNull();
        assertThat(LyricAligner.nearestOnset(BigDecimal.valueOf(5.0), notes, 0.12)).isNull();
        assertThat(LyricAligner.nearestOnset(BigDecimal.valueOf(1.0), List.of(), 0.12)).isNull();
    }

    @Test
    void exactMatchAndTiesPreferTheEarlierNote() {
        assertThat(LyricAligner.nearestOnset(BigDecimal.valueOf(2.0), notes, 0.12).midi()).isEqualTo(64);
        // 1.25 está a 0,25 de ambas: fora da folga de 0,12, mas dentro de 0,3 — a primeira vence o empate.
        assertThat(LyricAligner.nearestOnset(BigDecimal.valueOf(1.25), notes, 0.3).midi()).isEqualTo(60);
    }
}
