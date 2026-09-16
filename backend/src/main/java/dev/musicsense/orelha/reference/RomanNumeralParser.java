package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.harmony.KeyMode;
import dev.musicsense.orelha.harmony.PitchClasses;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê numerais romanos escritos à mão ou copiados do TheoryTab ("I V vi IV", "bVII", "♭VI", "V/vi",
 * "ii°", "IV6", "V7", "Isus4", "I64", "i(no3)") e reduz cada um a fundamental (semitons acima da
 * tônica) + família da tríade. O grau é lido **na escala do modo declarado**, como o TheoryTab escreve
 * (III em Fá menor é Lá♭; em Dó maior é Mi); acidentes deslocam a partir daí. Sem modo, escala maior —
 * a convenção do Orelha. "(no3)" (power chord) reduz a maior, como o SectionDeriver faz com os nossos.
 * Sufixos de 7ª, sus e inversão são ignorados de propósito: a comparação com o extrator é por fundamental e qualidade, o resto o BTC erra demais para contar.
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

    /** Graus 1–7 do modo em semitons; PHRYGIAN_DOMINANT e outros sem sétima natural caem na escala do enum. */
    static int[] scaleOf(KeyMode mode) {
        if (mode == null) {
            return MAJOR_SCALE;
        }
        int[] out = new int[7];
        int n = 0;
        for (int pc = 0; pc < 12 && n < 7; pc++) {
            if (PitchClasses.contains(mode.scale(), pc)) {
                out[n++] = pc;
            }
        }
        return n == 7 ? out : MAJOR_SCALE;
    }

    private RomanNumeralParser() {
    }

    /** Separa por espaço, vírgula, hífen, seta ou barra vertical; tokens não reconhecidos são ignorados. */
    public static List<Degree> parse(String text) {
        return parse(text, null);
    }

    public static List<Degree> parse(String text, KeyMode mode) {
        int[] scale = scaleOf(mode);
        List<Degree> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.split("[\\s,|→\\->]+")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            Degree d = parseOne(token, scale);
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    static Degree parseOne(String token) {
        return parseOne(token, MAJOR_SCALE);
    }

    static Degree parseOne(String token, int[] scale) {
        int slash = token.indexOf('/');
        if (slash > 0 && slash < token.length() - 1) {
            String head = token.substring(0, slash);
            String tail = token.substring(slash + 1);
            Degree target = parseOne(tail, scale);
            // "V/vi": dominante da mediante; "IV/V" e afins também são lidos como acorde sobre o alvo.
            if (target != null && !tail.matches("\\d+")) {
                Degree fn = parseOne(head, MAJOR_SCALE);   // "V/x": a dominante é sempre a 5ª justa acima
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
        int interval = scale[step];
        if (accidental.equals("b") || accidental.equals("♭")) {
            interval = (interval + 11) % 12;
        } else if (accidental.equals("#") || accidental.equals("♯")) {
            interval = (interval + 1) % 12;
        }
        boolean upper = Character.isUpperCase(numeral.charAt(0));
        Family family;
        if (suffix.contains("no3") || suffix.startsWith("5")) {
            family = Family.MAJOR;   // power chord: sem terça, reduz a maior como do nosso lado
        } else if (mark.equals("°") || mark.equals("o") || mark.equals("ø") || suffix.startsWith("dim")) {
            family = Family.DIMINISHED;
        } else if (mark.equals("+") || suffix.startsWith("aug")) {
            family = Family.AUGMENTED;
        } else {
            family = upper ? Family.MAJOR : Family.MINOR;
        }
        return new Degree(interval, family, token, false);
    }
}
