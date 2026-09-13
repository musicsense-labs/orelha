package dev.rifflab.harmony;

import dev.rifflab.harmony.ChordQuality.TriadFamily;

/**
 * Transição entre dois acordes consecutivos com fundamental.
 *
 * @param rootInterval intervalo ascendente entre fundamentais, 0–11
 * @param commonTones  notas comuns entre as tríades reduzidas (0 quando um dos lados não tem tríade)
 * @param relation     classificação categórica
 */
public record Transition(int rootInterval, int commonTones, ChordRelation relation) {

    public static Transition between(Chord from, Chord to) {
        if (!from.hasRoot() || !to.hasRoot()) {
            throw new IllegalArgumentException("Both chords need a root");
        }
        int interval = PitchClasses.interval(from.rootPc(), to.rootPc());
        int common = PitchClasses.commonTones(from.triad(), to.triad());
        return new Transition(interval, common, classify(from, to, interval, common));
    }

    private static ChordRelation classify(Chord from, Chord to, int interval, int common) {
        TriadFamily a = from.quality().triadFamily();
        TriadFamily b = to.quality().triadFamily();
        if (interval == 0) {
            if (from.quality() == to.quality()) {
                return ChordRelation.SAME;
            }
            if (isMajorMinorPair(a, b)) {
                return ChordRelation.PARALLEL;
            }
            return ChordRelation.SAME_ROOT;
        }
        if (isThird(interval)) {
            return classifyThird(a, b, interval, common);
        }
        return switch (interval) {
            case 5 -> ChordRelation.FIFTH_DOWN;
            case 7 -> ChordRelation.FIFTH_UP;
            case 6 -> ChordRelation.TRITONE;
            case 1, 11 -> ChordRelation.SEMITONE;
            default -> ChordRelation.WHOLE_TONE; // 2, 10
        };
    }

    private static ChordRelation classifyThird(TriadFamily a, TriadFamily b, int interval, int common) {
        if (a == TriadFamily.NONE || b == TriadFamily.NONE) {
            return ChordRelation.MEDIANT;
        }
        if (isMajorMinorPair(a, b)) {
            // Intervalo maior→menor; a volta (menor→maior) é o complemento.
            int majorToMinor = a == TriadFamily.MAJOR ? interval : PitchClasses.pc(-interval);
            switch (majorToMinor) {
                case 9:
                    return ChordRelation.RELATIVE;         // C → Am
                case 4:
                    return ChordRelation.LEITTONWECHSEL;   // C → Em
                case 8:
                    return ChordRelation.HEXATONIC_POLE;   // C → A♭m
                default:
                    break;
            }
        }
        return switch (common) {
            case 2 -> ChordRelation.DIATONIC_MEDIANT;
            case 1 -> ChordRelation.CHROMATIC_MEDIANT;
            default -> ChordRelation.DOUBLY_CHROMATIC_MEDIANT;
        };
    }

    private static boolean isThird(int interval) {
        return interval == 3 || interval == 4 || interval == 8 || interval == 9;
    }

    private static boolean isMajorMinorPair(TriadFamily a, TriadFamily b) {
        return (a == TriadFamily.MAJOR && b == TriadFamily.MINOR) || (a == TriadFamily.MINOR && b == TriadFamily.MAJOR);
    }
}
