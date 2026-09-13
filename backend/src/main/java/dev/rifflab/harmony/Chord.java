package dev.rifflab.harmony;

/**
 * Acorde como classe de altura: fundamental, qualidade e baixo (se conhecido).
 * rootPc é null exatamente quando a qualidade não tem fundamental (NO_CHORD/UNKNOWN).
 */
public record Chord(Integer rootPc, ChordQuality quality, Integer bassPc) {

    public Chord {
        if (quality.hasRoot() != (rootPc != null)) {
            throw new IllegalArgumentException(quality + " " + (rootPc == null ? "requires" : "cannot have") + " a root");
        }
        if (rootPc != null && (rootPc < 0 || rootPc > 11)) {
            throw new IllegalArgumentException("rootPc out of range: " + rootPc);
        }
        if (bassPc != null && (bassPc < 0 || bassPc > 11)) {
            throw new IllegalArgumentException("bassPc out of range: " + bassPc);
        }
    }

    public static Chord of(int rootPc, ChordQuality quality) {
        return new Chord(rootPc, quality, null);
    }

    public static Chord noChord() {
        return new Chord(null, ChordQuality.NO_CHORD, null);
    }

    public boolean hasRoot() {
        return rootPc != null;
    }

    /** Todas as notas do acorde (tríade + extensões) como máscara de pcs. */
    public int pitchClasses() {
        return hasRoot() ? PitchClasses.transpose(quality.intervals(), rootPc) : 0;
    }

    /** Só a tríade (7ªs e 6ªs descartadas); vazio para sus/power. */
    public int triad() {
        return hasRoot() ? PitchClasses.transpose(quality.triadIntervals(), rootPc) : 0;
    }

    public boolean isInverted() {
        return hasRoot() && bassPc != null && !bassPc.equals(rootPc);
    }
}
