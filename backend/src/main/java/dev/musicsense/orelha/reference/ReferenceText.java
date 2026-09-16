package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.harmony.KeyMode;
import dev.musicsense.orelha.reference.ReferenceAnalysis.Section;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o texto que o dono cola a partir do TheoryTab:
 * <pre>
 * tonalidade: G major        (ou "key: Sol maior", "tom: Em")
 * Intro: I III IV iv
 * Verse: I III IV iv
 * Chorus: I III IV iv
 * </pre>
 * Linha "rótulo: progressão" abre uma seção; linha sem dois-pontos continua a anterior. Java puro.
 */
public final class ReferenceText {

    public record Parsed(Integer tonicPc, KeyMode mode, List<Section> sections) {
    }

    private static final Pattern KEY_LINE = Pattern.compile("^\\s*(tonalidade|key|tom|tonality)\\s*[:=]\\s*(.+?)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SECTION_LINE = Pattern.compile("^\\s*([A-Za-zÀ-ú0-9 '\\-\\.]{1,40}?)\\s*:\\s*(.*)$");
    private static final Pattern NOTE = Pattern.compile("^([A-Ga-g]|do|ré|re|mi|fá|fa|sol|lá|la|si)([#♯b♭]?)\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> NOTE_PC = Map.ofEntries(
            Map.entry("c", 0), Map.entry("do", 0), Map.entry("d", 2), Map.entry("re", 2), Map.entry("ré", 2),
            Map.entry("e", 4), Map.entry("mi", 4), Map.entry("f", 5), Map.entry("fa", 5), Map.entry("fá", 5),
            Map.entry("g", 7), Map.entry("sol", 7), Map.entry("a", 9), Map.entry("la", 9), Map.entry("lá", 9),
            Map.entry("b", 11), Map.entry("si", 11));

    private ReferenceText() {
    }

    public static Parsed parse(String text) {
        Integer tonic = null;
        KeyMode mode = null;
        List<Section> sections = new ArrayList<>();
        for (String line : (text == null ? "" : text).split("\\r?\\n")) {
            if (line.isBlank()) {
                continue;
            }
            Matcher key = KEY_LINE.matcher(line);
            if (key.matches()) {
                Matcher note = NOTE.matcher(key.group(2).trim());
                if (note.matches()) {
                    int pc = NOTE_PC.get(note.group(1).toLowerCase(Locale.ROOT));
                    String acc = note.group(2);
                    if (acc.equals("#") || acc.equals("♯")) {
                        pc = (pc + 1) % 12;
                    } else if (acc.equals("b") || acc.equals("♭")) {
                        pc = (pc + 11) % 12;
                    }
                    tonic = pc;
                    mode = modeOf(note.group(3));
                }
                continue;
            }
            Matcher section = SECTION_LINE.matcher(line);
            if (section.matches()) {
                sections.add(new Section(section.group(1).trim(), section.group(2).trim()));
            } else if (!sections.isEmpty()) {
                Section last = sections.remove(sections.size() - 1);
                sections.add(new Section(last.label(), (last.progression() + " " + line.trim()).trim()));
            } else {
                sections.add(new Section("?", line.trim()));
            }
        }
        return new Parsed(tonic, mode, sections);
    }

    /** "major", "maior", "M", "m", "minor", "menor", "mixolydian", "mixolídio", "dorian"… */
    static KeyMode modeOf(String rest) {
        String r = rest == null ? "" : rest.trim().toLowerCase(Locale.ROOT);
        if (r.isEmpty() || r.startsWith("maj") || r.startsWith("maior") || r.equals("M")) {
            return KeyMode.MAJOR;
        }
        if (r.startsWith("min") || r.startsWith("menor") || r.equals("m")) {
            return KeyMode.MINOR;
        }
        for (KeyMode m : KeyMode.values()) {
            String name = m.name().toLowerCase(Locale.ROOT);
            if (r.startsWith(name.substring(0, Math.min(4, name.length())))) {
                return m;
            }
        }
        return KeyMode.MAJOR;
    }
}
