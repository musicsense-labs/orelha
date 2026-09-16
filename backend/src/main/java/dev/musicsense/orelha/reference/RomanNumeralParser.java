package dev.musicsense.orelha.reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê numerais romanos escritos à mão ou copiados do TheoryTab ("I V vi IV", "bVII", "♭VI", "V/vi",
 * "ii°", "IV6", "V7", "Isus4", "I64") e reduz cada um a fundamental (semitons acima da tônica, na
 * escala maior) + família da tríade. Sufixos de 7ª, sus e inversão são ignorados de propósito: a
 * comparação com o extrator é por fundamental e qualidade, o resto o BTC erra demais para contar.
 * Dominante secundária "V/x" vira a tríade maior uma 5ª acima de x. Java puro.
 */
public final class RomanNumeralParser {

    public enum Family { MAJOR, MINOR, DIMINISHED, AUGMENTED }

    /** {@code degreeInterval} = semitons acima da tônica (0–11); {@code applied} = veio de "V/x". */
    public record Degree(int degreeInterval, Family family, String source, boolean applied) {

        /** Chave de comparação: fundamental + família ("10:MAJOR" = ♭VII). */
        public String key() {
            return degreeInterval + ":" + family;
        }
    }

    private static final Pattern TOKEN = Pattern.compile(
            "^([b♭#♯]?)(VII|VI|IV|V|III|II|I|vii|vi|iv|v|iii|ii|i)([°oø+]?)(.*)$");
    private static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11};

    private RomanNumeralParser() {
    }

    /** Separa por espaço, vírgula, hífen, seta ou barra vertical; tokens não reconhecidos são ignorados. */
    public static List<Degree> parse(String text) {
        List<Degree> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.split("[\\s,|→\\->]+")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            Degree d = parseOne(token);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    static Degree parseOne(String token) {
        int slash = token.indexOf('/');
        if (slash > 0 && slash < token.length() - 1) {
            String head = token.substring(0, slash);
            String tail = token.substring(slash + 1);
            Degree target = parseOne(tail);
            // "V/vi": dominante da mediante; "IV/V" e afins também são lidos como acorde sobre o alvo.
            if (target != null && !tail.matches("\\d+")) {
                Degree fn = parseOne(head);
                if (fn != null) {
                    return new Degree((target.degreeInterval() + fn.degreeInterval()) % 12, fn.family(), token, true);
                }
            }
            token = head;   // "I/3", "V/5" (inversão em número): fica só a cabeça
        }
        Matcher m = TOKEN.matcher(token);
        if (!m.matches()) {
            return null;
        }
        String accidental = m.group(1);
        String numeral = m.group(2);
        String mark = m.group(3);
        String suffix = m.group(4).toLowerCase(Locale.ROOT);
        int step = switch (numeral.toUpperCase(Locale.ROOT)) {
            case "I" -> 0;
            case "II" -> 1;
            case "III" -> 2;
            case "IV" -> 3;
            case "V" -> 4;
            case "VI" -> 5;
            default -> 6;
        };
        int interval = MAJOR_SCALE[step];
        if (accidental.equals("b") || accidental.equals("♭")) {
            interval = (interval + 11) % 12;
        } else if (accidental.equals("#") || accidental.equals("♯")) {
            interval = (interval + 1) % 12;
        }
        boolean upper = Character.isUpperCase(numeral.charAt(0));
        Family family;
        if (mark.equals("°") || mark.equals("o") || mark.equals("ø") || suffix.startsWith("dim")) {
            family = Family.DIMINISHED;
        } else if (mark.equals("+") || suffix.startsWith("aug")) {
            family = Family.AUGMENTED;
        } else {
            family = upper ? Family.MAJOR : Family.MINOR;
        }
        return new Degree(interval, family, token, false);
    }
}
