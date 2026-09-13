package dev.rifflab.harmony;

/**
 * Modo de referência de um trecho tonal.
 * MAJOR/MINOR são o que os extratores devolvem. MINOR e DORIAN são "menores tonais": a dominante da
 * harmônica (V, V7, vii°, vii°7) conta como diatônica. AEOLIAN, PHRYGIAN e os demais são estritos —
 * referências derivadas ou manuais (P2).
 */
public enum KeyMode {
    MAJOR(false, 0, 2, 4, 5, 7, 9, 11),
    /** Menor tonal: eólio como base + dominante da harmônica. */
    MINOR(true, 0, 2, 3, 5, 7, 8, 10),
    /** Menor tonal de 6ª maior (IV maior, ii menor) + dominante da harmônica. */
    DORIAN(true, 0, 2, 3, 5, 7, 9, 10),
    PHRYGIAN(false, 0, 1, 3, 5, 7, 8, 10),
    /** 5º modo da menor harmônica: tônica maior com ♭II (flamenco, metal). */
    PHRYGIAN_DOMINANT(false, 0, 1, 4, 5, 7, 8, 10),
    LYDIAN(false, 0, 2, 4, 6, 7, 9, 11),
    MIXOLYDIAN(false, 0, 2, 4, 5, 7, 9, 10),
    AEOLIAN(false, 0, 2, 3, 5, 7, 8, 10),
    LOCRIAN(false, 0, 1, 3, 5, 6, 8, 10);

    static final int HARMONIC_MINOR = PitchClasses.mask(0, 2, 3, 5, 7, 8, 11);

    private final boolean harmonicDominant;
    private final int scale;

    KeyMode(boolean harmonicDominant, int... degrees) {
        this.harmonicDominant = harmonicDominant;
        this.scale = PitchClasses.mask(degrees);
    }

    /** Escala do modo com tônica em 0, como máscara de pcs. */
    public int scale() {
        return scale;
    }

    /** Menor tonal: V/V7/vii°/vii°7 da harmônica são diatônicos. */
    public boolean hasHarmonicDominant() {
        return harmonicDominant;
    }

    /** Terça da tônica é maior? Decide o modo paralelo para empréstimo e a grafia ♯IV/♭V. */
    public boolean isMajorThird() {
        return PitchClasses.contains(scale, 4);
    }

    /** Modo de onde se empresta: maior ↔ menor natural (definição do projeto para MODAL_BORROWING). */
    public KeyMode parallel() {
        return isMajorThird() ? MINOR : MAJOR;
    }
}
