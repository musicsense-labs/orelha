package dev.rifflab.harmony;

/**
 * Decide POWER a partir do chroma grave do segmento: um modelo maj/min sempre "escolhe" uma terça
 * para E5; aqui a terça precisaria aparecer no áudio.
 *
 * <p><strong>Desligado por padrão</strong> ({@code thirdRatio <= 0}). Medição de 2026-09-14 em três
 * faixas reais (chroma do stem de guitarra, C2–F4, razão terça/quinta): power chords distorcidos
 * (Teen Spirit) mediana 0,66; tríades limpas (Valerie) mediana 0,66; tríades distorcidas (Creep)
 * mediana 1,24. A intermodulação de fundamental e quinta sob distorção gera 2,5f — a terça maior uma
 * oitava acima, no mesmo registro em que a pestana a tocaria. Não há limiar que separe power chord
 * distorcido de tríade limpa por energia de classe de altura. Ligar só com um extrator que devolva
 * uma classe "5" ou outra evidência.
 */
public final class PowerChordDetector {

    /** Valor usado nos testes de unidade; em produção o padrão é 0 (desligado). */
    public static final double DEFAULT_THIRD_RATIO = 0.35;

    private final double thirdRatio;

    /** @param thirdRatio energia máxima da terça relativa à quinta; {@code <= 0} desliga a inferência. */
    public PowerChordDetector(double thirdRatio) {
        this.thirdRatio = thirdRatio;
    }

    public boolean isEnabled() {
        return thirdRatio > 0;
    }

    public boolean isPowerChord(float[] chroma, int rootPc) {
        if (!isEnabled() || chroma == null || chroma.length != 12) {
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
