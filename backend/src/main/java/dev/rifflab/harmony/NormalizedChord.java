package dev.rifflab.harmony;

/**
 * Saída do normalizer para um acorde da sequência.
 *
 * @param index          posição na sequência de entrada
 * @param chord          o acorde de entrada
 * @param degreeInterval (root − tônica) mod 12; null sem fundamental
 * @param degreeLabel    numeral romano renderizado; null sem fundamental
 * @param keyRelation    eixo A
 * @param bassRole       papel do baixo
 * @param fromPrevious   eixo B; null no primeiro acorde ou quando um dos lados não tem fundamental
 */
public record NormalizedChord(int index, Chord chord, Integer degreeInterval, String degreeLabel,
                              KeyRelation keyRelation, BassRole bassRole, Transition fromPrevious) {

    public boolean isInverted() {
        return chord.isInverted();
    }
}
