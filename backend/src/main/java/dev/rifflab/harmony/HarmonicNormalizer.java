package dev.rifflab.harmony;

import java.util.ArrayList;
import java.util.List;

/**
 * O núcleo do projeto: dado uma sequência de acordes e a tonalidade de referência, deriva grau,
 * relação com a tonalidade (eixo A), papel do baixo e relação com o acorde anterior (eixo B).
 * Java puro, sem I/O. A sequência deve chegar já fundida (sem acordes idênticos consecutivos).
 */
public final class HarmonicNormalizer {

    /** Versão gravada em harmonic_annotation.normalizer_version; mude a cada alteração de regra. */
    public static final String VERSION = "1.0";

    public List<NormalizedChord> normalize(List<Chord> chords, Key key) {
        List<NormalizedChord> result = new ArrayList<>(chords.size());
        Chord previous = null;
        for (int i = 0; i < chords.size(); i++) {
            Chord chord = chords.get(i);
            Chord next = i + 1 < chords.size() ? chords.get(i + 1) : null;
            result.add(normalizeOne(i, chord, previous, next, key));
            previous = chord;
        }
        return result;
    }

    private NormalizedChord normalizeOne(int index, Chord chord, Chord previous, Chord next, Key key) {
        if (!chord.hasRoot()) {
            return new NormalizedChord(index, chord, null, null, KeyRelation.NONE, BassRole.UNKNOWN, null);
        }
        int degree = key.degreeInterval(chord.rootPc());
        Transition transition = previous != null && previous.hasRoot() ? Transition.between(previous, chord) : null;
        return new NormalizedChord(index, chord, degree, RomanNumeral.label(degree, chord.quality(), key.mode()),
                keyRelation(chord, next, key), BassRole.of(chord), transition);
    }

    /** Eixo A, na precedência documentada em {@link KeyRelation}. */
    static KeyRelation keyRelation(Chord chord, Chord next, Key key) {
        if (chord.quality() == ChordQuality.POWER) {
            // Díade só: se cabe na escala, a terça decidiria entre maior e menor — e não inferimos.
            if (PitchClasses.isSubset(chord.pitchClasses(), key.scale())) {
                return KeyRelation.AMBIGUOUS;
            }
            return key.isBorrowed(chord) ? KeyRelation.BORROWED : KeyRelation.CHROMATIC;
        }
        if (key.isDiatonic(chord)) {
            return KeyRelation.DIATONIC;
        }
        if (key.isBorrowed(chord)) {
            return KeyRelation.BORROWED;
        }
        if (isSecondaryDominant(chord, next)) {
            return KeyRelation.SECONDARY_DOMINANT;
        }
        return KeyRelation.CHROMATIC;
    }

    /**
     * Dominante secundária: uma DOM7 fora do campo é dominante pela própria qualidade, resolva ou não
     * (E7 → F em Dó maior continua V/vi). Uma tríade maior só quando o próximo acorde está uma 5ª abaixo.
     */
    private static boolean isSecondaryDominant(Chord chord, Chord next) {
        if (chord.quality() == ChordQuality.DOM7) {
            return true;
        }
        return chord.quality() == ChordQuality.MAJ && next != null && next.hasRoot()
                && PitchClasses.interval(chord.rootPc(), next.rootPc()) == 5;
    }
}
