package dev.rifflab.harmony;

/**
 * Decide POWER a partir do chroma do segmento: um modelo maj/min sempre "escolhe" uma terça para
 * E5; aqui a terça precisa aparecer no áudio. Limiar provisório, a calibrar no portão da Onda 2.
 */
public final class PowerChordDetector {

    public static final double DEFAULT_THIRD_RATIO = 0.35;

    private final double thirdRatio;

    /** @param thirdRatio energia máxima da terça (maior ou menor) relativa à média de fundamental e quinta. */
    public PowerChordDetector(double thirdRatio) {
        this.thirdRatio = thirdRatio;
    }

    public boolean isPowerChord(float[] chroma, int rootPc) {
        if (chroma == null || chroma.length != 12) {
            return false;
        }
        double root = chroma[PitchClasses.pc(rootPc)];
        double fifth = chroma[PitchClasses.pc(rootPc + 7)];
        double third = Math.max(chroma[PitchClasses.pc(rootPc + 3)], chroma[PitchClasses.pc(rootPc + 4)]);
        double reference = (root + fifth) / 2;
        return reference > 0 && third < thirdRatio * reference;
    }

    /** Tríades maiores/menores sem terça audível viram POWER; o resto passa intacto. */
    public Chord reclassify(Chord chord, float[] chroma) {
        boolean triad = chord.quality() == ChordQuality.MAJ || chord.quality() == ChordQuality.MIN;
        if (triad && isPowerChord(chroma, chord.rootPc())) {
            return new Chord(chord.rootPc(), ChordQuality.POWER, chord.bassPc());
        }
        return chord;
    }
}
