package dev.musicsense.orelha.practice;

import dev.musicsense.orelha.practice.BassMidiExporter.Note;
import org.junit.jupiter.api.Test;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BassMidiExporterTest {

    @Test
    void notesLandOnTheBeatGridAtSixteenths() {
        // Beats a 0,5 s (120 BPM); nota em 1,02 s = beat 2 + 4 % → tick 964 → semicolcheia mais próxima = 960.
        List<Double> beats = List.of(0.0, 0.5, 1.0, 1.5, 2.0);
        assertThat(BassMidiExporter.ticksAt(1.02, beats)).isCloseTo(2.04 * 480, within(0.01));
        assertThat(BassMidiExporter.quantize(2.04 * 480)).isEqualTo(960);
        assertThat(BassMidiExporter.quantize(2.13 * 480)).isEqualTo(1080);   // 1022 → 1080 (8,5 semicolcheias → 9)
        assertThat(BassMidiExporter.medianBeatSeconds(beats)).isEqualTo(0.5);
    }

    @Test
    void exportedFileReadsBackWithTempoAndNotes() throws Exception {
        List<Double> beats = List.of(0.0, 0.5, 1.0, 1.5, 2.0, 2.5);
        byte[] bytes = BassMidiExporter.export("Creep — baixo", List.of(
                new Note(0.0, 0.45, 43, 90),
                new Note(1.0, 1.4, 45, 80)), beats, 4);
        Sequence seq = MidiSystem.getSequence(new ByteArrayInputStream(bytes));
        assertThat(seq.getResolution()).isEqualTo(480);
        Track track = seq.getTracks()[0];
        List<int[]> ons = new ArrayList<>();
        for (int i = 0; i < track.size(); i++) {
            MidiEvent e = track.get(i);
            if (e.getMessage() instanceof ShortMessage sm && sm.getCommand() == ShortMessage.NOTE_ON) {
                ons.add(new int[]{(int) e.getTick(), sm.getData1()});
            }
        }
        assertThat(ons).containsExactly(new int[]{0, 43}, new int[]{960, 45});
    }
}
