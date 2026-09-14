package dev.rifflab.harmony;

/**
 * Decide POWER a partir do chroma do segmento: um modelo maj/min sempre "escolhe" uma terça para
 * E5; aqui a terça precisa aparecer no áudio.
 *
 * <p>A referência é a energia da <em>quinta</em>, não da fundamental: a fundamental chega inflada
 * pelo baixo e pelos harmônicos (numa tríade completa a terça fica em ~0,2 da fundamental, mas em
 * 0,55–0,70 da quinta; o vazamento da terça ausente fica em ~0,2 da quinta). Limiar provisório,
 * medido no WAV sintético; calibrar com guitarra distorcida no portão da Onda 2.
 */
public final class PowerChordDetector {

    public static final double DEFAULT_THIRD_RATIO = 0.35;

    private final double thirdRatio;

    /** @param thirdRatio energia máxima da terça (maior ou menor) relativa à energia da quinta. */
    public PowerChordDetector(double thirdRatio) {
        this.thirdRatio = thirdRatio;
    }

    public boolean isPowerChord(float[] chroma, int rootPc) {
        if (chroma == null || chroma.length != 12) {
            return false;
        }
        double fifth = chroma[PitchClasses.pc(rootPc + 7)];
        double third = Math.max(chroma[PitchClasses.pc(rootPc + 3)], chroma[PitchClasses.pc(rootPc + 4)]);
        double reference = fifth > 0 ? fifth : chroma[PitchClasses.pc(rootPc)];
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
