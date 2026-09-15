package dev.musicsense.orelha.harmony;

import dev.musicsense.orelha.harmony.ChordQuality.TriadFamily;

/**
 * Renderização do grau. Numerais sempre relativos à escala MAIOR da tônica (convenção Chediak/pop):
 * ♭III, ♭VI, ♭VII também em menor. O trítono é ♯IV em modos de terça maior (lídio) e ♭V nos de
 * terça menor (blue note). Caixa baixa para tríade menor/diminuta; power e sus em caixa alta com sufixo.
 */
public final class RomanNumeral {

    private static final String[] MAJOR_SCALE_LABELS = {
            "I", "♭II", "II", "♭III", "III", "IV", "♯IV", "V", "♭VI", "VI", "♭VII", "VII"
    };

    private RomanNumeral() {
    }

    public static String label(int degreeInterval, ChordQuality quality, KeyMode mode) {
        int interval = PitchClasses.pc(degreeInterval);
        String numeral = interval == 6 && !mode.isMajorThird() ? "♭V" : MAJOR_SCALE_LABELS[interval];
        TriadFamily family = quality.triadFamily();
        if (family == TriadFamily.MINOR || family == TriadFamily.DIMINISHED) {
            numeral = numeral.toLowerCase();
        }
        return numeral + quality.suffix();
    }
}
