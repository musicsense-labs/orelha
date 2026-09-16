package dev.musicsense.orelha.analysis;

import java.math.BigDecimal;
import java.util.List;

/**
 * Encaixa o ataque de cada palavra no ataque de nota de voz mais próximo (Java puro). Palavra e nota
 * vêm do mesmo stem, mas de modelos diferentes: o ASR marca o início da consoante, o basic-pitch o da
 * vogal cantada. Dentro da folga, o ataque da nota é o instante musical da palavra — é ele que a UI
 * usa para destacar a palavra cantada e que o compasso da palavra deveria seguir.
 */
public final class LyricAligner {

    /** Nota cujo ataque coincide com a palavra: instante e altura; null quando nenhuma cabe na folga. */
    public record Onset(BigDecimal startS, int midi) {
    }

    private LyricAligner() {
    }

    /** {@code notes} ordenadas por início. */
    public static Onset nearestOnset(BigDecimal wordStart, List<VocalNote> notes, double toleranceS) {
        double target = wordStart.doubleValue();
        VocalNote best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VocalNote n : notes) {
            double distance = Math.abs(n.getStartS().doubleValue() - target);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = n;
            } else if (n.getStartS().doubleValue() > target + toleranceS) {
                break;   // ordenadas: daqui em diante só se afastam
            }
        }
        return best == null || bestDistance > toleranceS ? null : new Onset(best.getStartS(), best.getMidiPitch());
    }
}
