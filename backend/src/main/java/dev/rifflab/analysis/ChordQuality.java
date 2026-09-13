package dev.rifflab.analysis;

/**
 * Vocabulário interno de qualidade de acorde. Cada adapter traduz o dialeto do seu extrator para cá.
 * POWER nunca vem do extrator: é decidido em Java a partir do chroma (ausência de terça).
 */
public enum ChordQuality {
    MAJ, MIN, DIM, AUG,
    MAJ6, MIN6,
    MAJ7, MIN7, DOM7, DIM7, HDIM7, MINMAJ7,
    SUS2, SUS4,
    POWER,
    /** Silêncio ou trecho sem harmonia ('N' nos extratores). */
    NO_CHORD,
    /** O extrator não soube rotular ('X'). */
    UNKNOWN;

    public boolean hasRoot() {
        return this != NO_CHORD && this != UNKNOWN;
    }
}
