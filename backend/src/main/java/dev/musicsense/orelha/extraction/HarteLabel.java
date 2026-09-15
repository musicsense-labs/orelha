package dev.musicsense.orelha.extraction;

import dev.musicsense.orelha.harmony.Chord;
import dev.musicsense.orelha.harmony.ChordQuality;
import dev.musicsense.orelha.harmony.PitchClasses;

import java.util.Map;

/**
 * Parser da notação Harte usada pelos modelos de acorde ("C", "C#:min7", "Bb:maj/3", "N", "X").
 * Vocabulário do BTC (170 classes): 12 raízes × 14 qualidades + N + X, sem inversões.
 */
public final class HarteLabel {

    private static final Map<String, ChordQuality> QUALITIES = Map.ofEntries(
            Map.entry("maj", ChordQuality.MAJ),
            Map.entry("min", ChordQuality.MIN),
            Map.entry("dim", ChordQuality.DIM),
            Map.entry("aug", ChordQuality.AUG),
            Map.entry("maj6", ChordQuality.MAJ6),
            Map.entry("min6", ChordQuality.MIN6),
            Map.entry("maj7", ChordQuality.MAJ7),
            Map.entry("min7", ChordQuality.MIN7),
            Map.entry("7", ChordQuality.DOM7),
            Map.entry("dim7", ChordQuality.DIM7),
            Map.entry("hdim7", ChordQuality.HDIM7),
            Map.entry("minmaj7", ChordQuality.MINMAJ7),
            Map.entry("sus2", ChordQuality.SUS2),
            Map.entry("sus4", ChordQuality.SUS4));

    private static final Map<Character, Integer> NATURALS = Map.of(
            'C', 0, 'D', 2, 'E', 4, 'F', 5, 'G', 7, 'A', 9, 'B', 11);

    private HarteLabel() {
    }

    public static Chord parse(String label) {
        if (label.equals("N")) {
            return Chord.noChord();
        }
        if (label.equals("X")) {
            return new Chord(null, ChordQuality.UNKNOWN, null);
        }
        String body = label.split("/", 2)[0]; // inversões por grau ("/3") são ignoradas: o baixo vem do stem
        int colon = body.indexOf(':');
        String note = colon < 0 ? body : body.substring(0, colon);
        String quality = colon < 0 ? "maj" : body.substring(colon + 1);
        ChordQuality chordQuality = QUALITIES.get(quality);
        if (chordQuality == null) {
            throw new IllegalArgumentException("Unknown Harte quality in label: " + label);
        }
        return Chord.of(pitchClass(note, label), chordQuality);
    }

    private static int pitchClass(String note, String label) {
        Integer natural = note.isEmpty() ? null : NATURALS.get(note.charAt(0));
        if (natural == null) {
            throw new IllegalArgumentException("Unknown Harte root in label: " + label);
        }
        int pc = natural;
        for (char accidental : note.substring(1).toCharArray()) {
            pc += switch (accidental) {
                case '#' -> 1;
                case 'b' -> -1;
                default -> throw new IllegalArgumentException("Unknown Harte root in label: " + label);
            };
        }
        return PitchClasses.pc(pc);
    }
}
