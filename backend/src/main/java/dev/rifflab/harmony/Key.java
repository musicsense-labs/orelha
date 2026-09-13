package dev.rifflab.harmony;

/** Tonalidade de referência: tônica (pc) e modo. */
public record Key(int tonicPc, KeyMode mode) {

    public Key {
        if (tonicPc < 0 || tonicPc > 11) {
            throw new IllegalArgumentException("tonicPc out of range: " + tonicPc);
        }
    }

    /** Escala de referência transposta para a tônica. */
    public int scale() {
        return PitchClasses.transpose(mode.scale(), tonicPc);
    }

    /** Escala do modo paralelo (maior ↔ menor natural) transposta para a tônica. */
    public int parallelScale() {
        return PitchClasses.transpose(mode.parallel().scale(), tonicPc);
    }

    /** Grau como intervalo: (root − tônica) mod 12. */
    public int degreeInterval(int rootPc) {
        return PitchClasses.interval(tonicPc, rootPc);
    }

    /**
     * O acorde pertence ao campo harmônico? Todas as notas na escala; em MINOR, além da natural,
     * V, V7, vii° e vii°7 da harmônica (função dominante) também contam.
     */
    public boolean isDiatonic(Chord chord) {
        if (!chord.hasRoot()) {
            return false;
        }
        int pcs = chord.pitchClasses();
        if (PitchClasses.isSubset(pcs, scale())) {
            return true;
        }
        if (mode == KeyMode.MINOR) {
            int degree = degreeInterval(chord.rootPc());
            boolean dominantFunction = degree == 7 || degree == 11;
            return dominantFunction
                    && PitchClasses.isSubset(pcs, PitchClasses.transpose(KeyMode.HARMONIC_MINOR, tonicPc));
        }
        return false;
    }

    public boolean isBorrowed(Chord chord) {
        return chord.hasRoot() && PitchClasses.isSubset(chord.pitchClasses(), parallelScale());
    }
}
