package dev.rifflab.harmony;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parser de cifras populares para os testes de progressão ("F#m7", "Bb5", "Am/G#", "A7sus4").
 * Extensões fora do vocabulário são reduzidas ao acorde mais próximo (add9 → tríade, 7sus4 → sus4,
 * 7#9 → 7, 9 → 7). Não é o parser dos extratores (esses falam Harte: "C:maj7").
 */
final class ChordSymbol {

    private static final Map<String, ChordQuality> SUFFIXES = Map.ofEntries(
            Map.entry("", ChordQuality.MAJ),
            Map.entry("m", ChordQuality.MIN),
            Map.entry("5", ChordQuality.POWER),
            Map.entry("7", ChordQuality.DOM7),
            Map.entry("9", ChordQuality.DOM7),
            Map.entry("7#9", ChordQuality.DOM7),
            Map.entry("m7", ChordQuality.MIN7),
            Map.entry("maj7", ChordQuality.MAJ7),
            Map.entry("m(maj7)", ChordQuality.MINMAJ7),
            Map.entry("dim", ChordQuality.DIM),
            Map.entry("°", ChordQuality.DIM),
            Map.entry("dim7", ChordQuality.DIM7),
            Map.entry("m7b5", ChordQuality.HDIM7),
            Map.entry("aug", ChordQuality.AUG),
            Map.entry("+", ChordQuality.AUG),
            Map.entry("sus4", ChordQuality.SUS4),
            Map.entry("7sus4", ChordQuality.SUS4),
            Map.entry("sus2", ChordQuality.SUS2),
            Map.entry("add9", ChordQuality.MAJ),
            Map.entry("6", ChordQuality.MAJ6),
            Map.entry("m6", ChordQuality.MIN6),
            Map.entry("N", ChordQuality.NO_CHORD));

    private ChordSymbol() {
    }

    static List<Chord> parseAll(String symbols) {
        List<Chord> chords = new ArrayList<>();
        for (String symbol : symbols.trim().split("\\s+")) {
            chords.add(parse(symbol));
        }
        return chords;
    }

    static Chord parse(String symbol) {
        if (symbol.equals("N")) {
            return Chord.noChord();
        }
        String[] parts = symbol.split("/", 2);
        int rootEnd = noteLength(parts[0]);
        int root = note(parts[0].substring(0, rootEnd));
        ChordQuality quality = SUFFIXES.get(parts[0].substring(rootEnd));
        if (quality == null) {
            throw new IllegalArgumentException("Unknown chord symbol: " + symbol);
        }
        Integer bass = parts.length == 2 ? note(parts[1]) : null;
        return new Chord(root, quality, bass);
    }

    static int note(String name) {
        int pc = switch (name.charAt(0)) {
            case 'C' -> 0;
            case 'D' -> 2;
            case 'E' -> 4;
            case 'F' -> 5;
            case 'G' -> 7;
            case 'A' -> 9;
            case 'B' -> 11;
            default -> throw new IllegalArgumentException("Unknown note: " + name);
        };
        if (name.length() > 1) {
            pc += switch (name.charAt(1)) {
                case '#' -> 1;
                case 'b' -> -1;
                default -> throw new IllegalArgumentException("Unknown note: " + name);
            };
        }
        return PitchClasses.pc(pc);
    }

    private static int noteLength(String symbol) {
        return symbol.length() > 1 && (symbol.charAt(1) == '#' || symbol.charAt(1) == 'b') ? 2 : 1;
    }
}
