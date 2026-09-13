package dev.rifflab.harmony;

/**
 * Eixo B: relação entre acordes consecutivos (ambos com fundamental), sobre as tríades reduzidas.
 * Mediantes e transformações neo-riemannianas só se qualificam entre tríades maiores/menores;
 * com sus/power/dim/aug a relação de terça fica como MEDIANT genérico.
 */
public enum ChordRelation {
    /** Mesma fundamental e mesma qualidade (não deveria ocorrer após fusão de segmentos). */
    SAME,
    /** P: mesma fundamental, maior ↔ menor. */
    PARALLEL,
    /** Mesma fundamental, outra mudança de qualidade (C → Csus4, C → C7). */
    SAME_ROOT,
    /** R: C ↔ Am (2 notas comuns). */
    RELATIVE,
    /** L: C ↔ Em, Am ↔ F (2 notas comuns). */
    LEITTONWECHSEL,
    /** L·P·L: C ↔ A♭m (0 notas comuns). */
    HEXATONIC_POLE,
    /** Terça, 2 notas comuns, mas não R/L (envolve dim/aug reduzidos). */
    DIATONIC_MEDIANT,
    /** Terça, 1 nota comum (C → E, C → A♭, C → E♭, C → A). A classe que caracteriza rock e metal. */
    CHROMATIC_MEDIANT,
    /** Terça, 0 notas comuns, não polo hexatônico (C → E♭m). */
    DOUBLY_CHROMATIC_MEDIANT,
    /** Terça entre acordes sem tríade (E5 → C5): notas comuns não decidem. */
    MEDIANT,
    /** Fundamental desce uma 5ª (G → C): movimento autêntico. */
    FIFTH_DOWN,
    /** Fundamental sobe uma 5ª (C → G). */
    FIFTH_UP,
    TRITONE,
    SEMITONE,
    WHOLE_TONE
}
