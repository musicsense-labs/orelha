package dev.musicsense.orelha.harmony;

/** Papel do baixo em relação ao acorde. */
public enum BassRole {
    /** Baixo desconhecido (extrator sem inversão e sem stem de baixo). */
    UNKNOWN,
    ROOT,
    THIRD,
    FIFTH,
    /** 7ª ou 6ª. */
    SEVENTH,
    /** 2ª ou 4ª de um sus. */
    SUSPENDED,
    /** Baixo fora do acorde: candidato a pedal. */
    NON_CHORD_TONE;

    public static BassRole of(Chord chord) {
        if (!chord.hasRoot() || chord.bassPc() == null) {
            return UNKNOWN;
        }
        int interval = PitchClasses.interval(chord.rootPc(), chord.bassPc());
        if (interval == 0) {
            return ROOT;
        }
        if (!PitchClasses.contains(chord.quality().intervals(), interval)) {
            return NON_CHORD_TONE;
        }
        return switch (interval) {
            case 3, 4 -> THIRD;
            case 6, 7, 8 -> FIFTH;
            case 2, 5 -> SUSPENDED;
            default -> SEVENTH; // 9, 10, 11: 6ª e 7ªs
        };
    }
}
