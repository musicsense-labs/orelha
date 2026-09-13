package dev.rifflab.harmony;

import dev.rifflab.harmony.ChordQuality.TriadFamily;

/**
 * Renderização do grau. Numerais sempre relativos à escala MAIOR da tônica (convenção Chediak/pop):
 * ♭III, ♭VI, ♭VII também em menor; ♯IV, nunca ♭V. Caixa baixa para tríade menor/diminuta;
 * power e sus em caixa alta com sufixo.
 */
public final class RomanNumeral {

    private static final String[] MAJOR_SCALE_LABELS = {
            "I", "♭II", "II", "♭III", "III", "IV", "♯IV", "V", "♭VI", "VI", "♭VII", "VII"
    };

    private RomanNumeral() {
    }

    public static String label(int degreeInterval, ChordQuality quality) {
        String numeral = MAJOR_SCALE_LABELS[PitchClasses.pc(degreeInterval)];
        TriadFamily family = quality.triadFamily();
        if (family == TriadFamily.MINOR || family == TriadFamily.DIMINISHED) {
            numeral = numeral.toLowerCase();
        }
        return numeral + quality.suffix();
    }
}
