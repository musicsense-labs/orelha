package dev.musicsense.orelha.practice;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A linha de baixo como arquivo MIDI (formato 0, uma trilha) para abrir no MuseScore ou TuxGuitar, que geram a
 * tablatura com ritmo. As notas são quantizadas na grade de beats do run — tick = beat × 480, interpolado
 * entre beats e arredondado à semicolcheia (120 ticks) — e o andamento gravado é um só (a mediana dos beats),
 * então a partitura fica na grade e não no tempo real do áudio. Sem beats, cai num 120 BPM fixo. Só o JDK
 * ({@code javax.sound.midi}); nenhuma dependência.
 */
public final class BassMidiExporter {

    public static final int PPQ = 480;
    private static final int SIXTEENTH = PPQ / 4;
    private static final int ELECTRIC_BASS_FINGER = 33;   // General MIDI, base 0

    public record Note(double startS, double endS, int midi, int velocity) {
    }

    private BassMidiExporter() {
    }

    /** {@code beatsS} em ordem crescente (pode ser vazio); {@code beatsPerBar} da fórmula de compasso (4 em 4/4). */
    public static byte[] export(String name, List<Note> notes, List<Double> beatsS, int beatsPerBar) {
        try {
            Sequence sequence = new Sequence(Sequence.PPQ, PPQ);
            Track track = sequence.createTrack();
            track.add(new MidiEvent(meta(0x03, name.getBytes(StandardCharsets.UTF_8)), 0));
            track.add(new MidiEvent(meta(0x58, new byte[]{(byte) beatsPerBar, 2, 24, 8}), 0));   // x/4
            track.add(new MidiEvent(tempo(medianBeatSeconds(beatsS)), 0));
            track.add(new MidiEvent(new ShortMessage(ShortMessage.PROGRAM_CHANGE, 0, ELECTRIC_BASS_FINGER, 0), 0));
            for (Note n : notes) {
                long on = quantize(ticksAt(n.startS(), beatsS));
                long off = Math.max(on + SIXTEENTH, quantize(ticksAt(n.endS(), beatsS)));
                int velocity = Math.min(127, Math.max(1, n.velocity()));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, n.midi(), velocity), on));
                track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, n.midi(), 0), off));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MidiSystem.write(sequence, 0, out);
            return out.toByteArray();
        } catch (InvalidMidiDataException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Posição em ticks: índice do beat (× PPQ) interpolado; fora da grade, extrapola pelo beat vizinho. */
    static double ticksAt(double seconds, List<Double> beats) {
        if (beats.size() < 2) {
            return seconds * 2 * PPQ;   // 120 BPM
        }
        int i = 0;
        while (i < beats.size() - 2 && beats.get(i + 1) <= seconds) {
            i++;
        }
        double b0 = beats.get(i);
        double b1 = beats.get(i + 1);
        double fraction = (seconds - b0) / (b1 - b0);
        return (i + fraction) * PPQ;
    }

    static long quantize(double ticks) {
        return Math.max(0, Math.round(ticks / SIXTEENTH)) * SIXTEENTH;
    }

    static double medianBeatSeconds(List<Double> beats) {
        if (beats.size() < 2) {
            return 0.5;
        }
        double[] gaps = new double[beats.size() - 1];
        for (int i = 0; i < gaps.length; i++) {
            gaps[i] = beats.get(i + 1) - beats.get(i);
        }
        java.util.Arrays.sort(gaps);
        return gaps[gaps.length / 2];
    }

    private static MetaMessage tempo(double secondsPerBeat) throws InvalidMidiDataException {
        int micros = (int) Math.round(secondsPerBeat * 1_000_000);
        return meta(0x51, new byte[]{(byte) (micros >> 16), (byte) (micros >> 8), (byte) micros});
    }

    private static MetaMessage meta(int type, byte[] data) throws InvalidMidiDataException {
        MetaMessage m = new MetaMessage();
        m.setMessage(type, data, data.length);
        return m;
    }
}
