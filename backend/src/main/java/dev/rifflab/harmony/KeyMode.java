package dev.rifflab.harmony;

/**
 * Modo de referência de um trecho tonal.
 * MAJOR/MINOR são o que os extratores devolvem e carregam a leitura tonal (MINOR inclui a dominante
 * da harmônica); os modos eclesiásticos são referências estritas, derivadas ou manuais (P2).
 */
public enum KeyMode {
    MAJOR(0, 2, 4, 5, 7, 9, 11),
    /** Menor tonal: eólio como base; V/V7/vii°/vii°7 da harmônica contam como diatônicos. */
    MINOR(0, 2, 3, 5, 7, 8, 10),
    DORIAN(0, 2, 3, 5, 7, 9, 10),
    PHRYGIAN(0, 1, 3, 5, 7, 8, 10),
    LYDIAN(0, 2, 4, 6, 7, 9, 11),
    MIXOLYDIAN(0, 2, 4, 5, 7, 9, 10),
    AEOLIAN(0, 2, 3, 5, 7, 8, 10),
    LOCRIAN(0, 1, 3, 5, 6, 8, 10);

    static final int HARMONIC_MINOR = PitchClasses.mask(0, 2, 3, 5, 7, 8, 11);

    private final int scale;

    KeyMode(int... degrees) {
        this.scale = PitchClasses.mask(degrees);
    }

    /** Escala do modo com tônica em 0, como máscara de pcs. */
    public int scale() {
        return scale;
    }

    /** Terça da tônica é maior? Decide qual é o modo paralelo para empréstimo. */
    public boolean isMajorThird() {
        return PitchClasses.contains(scale, 4);
    }

    /** Modo de onde se empresta: maior ↔ menor natural (definição do projeto para MODAL_BORROWING). */
    public KeyMode parallel() {
        return isMajorThird() ? MINOR : MAJOR;
    }
}
