package dev.rifflab.harmony;

/**
 * Vocabulário interno de qualidade de acorde. Cada adapter traduz o dialeto do seu extrator para cá.
 * POWER nunca vem do extrator: é decidido em Java a partir do chroma (ausência de terça).
 */
public enum ChordQuality {
    MAJ(TriadFamily.MAJOR, "", 0, 4, 7),
    MIN(TriadFamily.MINOR, "", 0, 3, 7),
    DIM(TriadFamily.DIMINISHED, "°", 0, 3, 6),
    AUG(TriadFamily.AUGMENTED, "+", 0, 4, 8),
    MAJ6(TriadFamily.MAJOR, "6", 0, 4, 7, 9),
    MIN6(TriadFamily.MINOR, "6", 0, 3, 7, 9),
    MAJ7(TriadFamily.MAJOR, "maj7", 0, 4, 7, 11),
    MIN7(TriadFamily.MINOR, "7", 0, 3, 7, 10),
    DOM7(TriadFamily.MAJOR, "7", 0, 4, 7, 10),
    DIM7(TriadFamily.DIMINISHED, "°7", 0, 3, 6, 9),
    HDIM7(TriadFamily.DIMINISHED, "ø7", 0, 3, 6, 10),
    MINMAJ7(TriadFamily.MINOR, "mM7", 0, 3, 7, 11),
    SUS2(TriadFamily.NONE, "sus2", 0, 2, 7),
    SUS4(TriadFamily.NONE, "sus4", 0, 5, 7),
    POWER(TriadFamily.NONE, "5", 0, 7),
    /** Silêncio ou trecho sem harmonia ('N' nos extratores). */
    NO_CHORD(TriadFamily.NONE, ""),
    /** O extrator não soube rotular ('X'). */
    UNKNOWN(TriadFamily.NONE, "");

    /** Tríade subjacente; decide caixa do numeral e participação em P/L/R. */
    public enum TriadFamily {
        MAJOR, MINOR, DIMINISHED, AUGMENTED, NONE
    }

    private final TriadFamily triadFamily;
    private final String suffix;
    private final int intervals;

    ChordQuality(TriadFamily triadFamily, String suffix, int... intervals) {
        this.triadFamily = triadFamily;
        this.suffix = suffix;
        this.intervals = PitchClasses.mask(intervals);
    }

    public boolean hasRoot() {
        return this != NO_CHORD && this != UNKNOWN;
    }

    public boolean hasThird() {
        return triadFamily != TriadFamily.NONE;
    }

    public TriadFamily triadFamily() {
        return triadFamily;
    }

    /** Sufixo do numeral romano: '', '7', 'maj7', '°', 'ø7', '+', 'sus4', '5'. */
    public String suffix() {
        return suffix;
    }

    /** Intervalos a partir da fundamental, como máscara de pcs (fundamental = bit 0). */
    public int intervals() {
        return intervals;
    }

    /** Intervalos só da tríade (sem 7ª/6ª); vazio para sus/power. */
    public int triadIntervals() {
        return switch (triadFamily) {
            case MAJOR -> MAJ.intervals;
            case MINOR -> MIN.intervals;
            case DIMINISHED -> DIM.intervals;
            case AUGMENTED -> AUG.intervals;
            case NONE -> 0;
        };
    }
}
