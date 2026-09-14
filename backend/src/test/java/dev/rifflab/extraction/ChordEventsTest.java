package dev.rifflab.extraction;

import dev.rifflab.extraction.ExtractionResult.ChordEvent;
import dev.rifflab.harmony.Chord;
import dev.rifflab.harmony.ChordQuality;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChordEventsTest {

    private static ChordEvent event(double start, double end, Chord chord, float first, float firstLow) {
        float[] chroma = new float[12];
        chroma[0] = first;
        float[] low = new float[12];
        low[0] = firstLow;
        return new ChordEvent(BigDecimal.valueOf(start), BigDecimal.valueOf(end), chord, chroma, low, null);
    }

    @Test
    void mergesIdenticalNeighboursWeightingBothChromasByDuration() {
        Chord am = Chord.of(9, ChordQuality.MIN);
        List<ChordEvent> merged = ChordEvents.mergeConsecutive(List.of(
                event(0, 1, am, 1.0f, 0.4f), event(1, 4, am, 0.0f, 0.8f), event(4, 5, Chord.of(5, ChordQuality.MAJ), 0.5f, 0.5f)));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).startS()).isEqualByComparingTo("0");
        assertThat(merged.get(0).endS()).isEqualByComparingTo("4");
        assertThat(merged.get(0).chroma()[0]).isEqualTo(0.25f);      // (1.0·1 + 0.0·3) / 4
        assertThat(merged.get(0).chromaLow()[0]).isEqualTo(0.7f);    // (0.4·1 + 0.8·3) / 4
        assertThat(merged.get(1).chord().rootPc()).isEqualTo(5);
    }

    @Test
    void missingLowChromaOnOneSideKeepsTheOther() {
        Chord am = Chord.of(9, ChordQuality.MIN);
        ChordEvent withLow = event(0, 1, am, 1, 0.9f);
        ChordEvent withoutLow = new ChordEvent(BigDecimal.ONE, BigDecimal.TWO, am, new float[12], null, null);
        assertThat(ChordEvents.mergeConsecutive(List.of(withLow, withoutLow)).get(0).chromaLow()[0]).isEqualTo(0.9f);
    }

    @Test
    void differentBassKeepsSegmentsApart() {
        Chord am = Chord.of(9, ChordQuality.MIN);
        Chord amOverGs = new Chord(9, ChordQuality.MIN, 8);
        assertThat(ChordEvents.mergeConsecutive(List.of(event(0, 1, am, 1, 1), event(1, 2, amOverGs, 1, 1)))).hasSize(2);
    }

    @Test
    void noChordSegmentsMergeToo() {
        assertThat(ChordEvents.mergeConsecutive(List.of(
                event(0, 1, Chord.noChord(), 0, 0), event(1, 2, Chord.noChord(), 0, 0)))).hasSize(1);
    }
}
