package dev.rifflab.harmony;

/**
 * Conjuntos de classes de altura como máscara de 12 bits (bit n = pc n, C = 0).
 * Toda a aritmética do normalizer passa por aqui; nada de listas.
 */
public final class PitchClasses {

    public static final int ALL = 0b1111_1111_1111;

    private PitchClasses() {
    }

    public static int pc(int n) {
        return Math.floorMod(n, 12);
    }

    public static int interval(int fromPc, int toPc) {
        return pc(toPc - fromPc);
    }

    public static int mask(int... pcs) {
        int mask = 0;
        for (int p : pcs) {
            mask |= 1 << pc(p);
        }
        return mask;
    }

    public static int transpose(int mask, int semitones) {
        int n = pc(semitones);
        return ((mask << n) | (mask >>> (12 - n))) & ALL;
    }

    public static boolean contains(int mask, int p) {
        return (mask & (1 << pc(p))) != 0;
    }

    public static boolean isSubset(int subset, int superset) {
        return (subset & ~superset) == 0;
    }

    public static int commonTones(int a, int b) {
        return Integer.bitCount(a & b);
    }
}
