package dev.rifflab.harmony;

/**
 * Eixo A: o acorde em relação à tonalidade de referência. Um rótulo por acorde, nesta precedência.
 * Relações com o acorde anterior (mediantes, P/L/R) vivem no eixo B: {@link ChordRelation}.
 */
public enum KeyRelation {
    /** Sem fundamental: NO_CHORD / UNKNOWN. */
    NONE,
    /** Power chord cuja díade cabe na escala: maior ou menor diatônico, a terça decidiria. Nunca inferir. */
    AMBIGUOUS,
    /** Todas as notas no campo harmônico da referência. */
    DIATONIC,
    /** Todas as notas no campo harmônico paralelo (maior ↔ menor natural). */
    BORROWED,
    /** Maior/dominante cuja fundamental está uma 5ª acima da fundamental do próximo acorde. */
    SECONDARY_DOMINANT,
    /** Fora de ambos os campos (♭II frígio, III/VI maiores em maior, ...). */
    CHROMATIC
}
