package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.reference.RomanNumeralParser.Degree;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Compara a análise humana (TheoryTab, transcrita pelo dono) com a nossa (extrator + normalizador),
 * seção a seção. Java puro: recebe as duas progressões já reduzidas a "fundamental:família" e mede
 * (1) similaridade de sequência = 1 − distância de edição / comprimento maior e (2) cobertura do
 * vocabulário = fração dos acordes da referência que aparecem na nossa parte. Repetições consecutivas
 * são fundidas antes (um "I I IV IV" e um "I IV" são a mesma progressão para este teste).
 */
public final class ReferenceComparer {

    /** Uma parte nossa (rótulo A, B, C… e a progressão de um ciclo). */
    public record OurPart(String label, List<String> keys) {
    }

    /** Resultado por seção da referência: a parte nossa mais parecida e as duas medidas. */
    public record SectionMatch(String referenceLabel, List<String> referenceKeys, String ourLabel,
                               List<String> ourKeys, double sequenceSimilarity, double vocabularyCoverage,
                               List<String> missingKeys) {
    }

    private ReferenceComparer() {
    }

    public static List<String> keysOf(List<Degree> degrees) {
        return dedupe(degrees.stream().map(Degree::key).toList());
    }

    public static SectionMatch match(String referenceLabel, List<String> referenceKeys, List<OurPart> ours) {
        List<String> ref = dedupe(referenceKeys);
        SectionMatch best = null;
        for (OurPart part : ours) {
            List<String> mine = dedupe(part.keys());
            double similarity = similarity(ref, mine);
            double coverage = coverage(ref, mine);
            double score = similarity + coverage;
            if (best == null || score > best.sequenceSimilarity() + best.vocabularyCoverage()) {
                best = new SectionMatch(referenceLabel, ref, part.label(), mine, similarity, coverage, missing(ref, mine));
            }
        }
        return best == null ? new SectionMatch(referenceLabel, ref, null, List.of(), 0, 0, ref) : best;
    }

    static double similarity(List<String> a, List<String> b) {
        int max = Math.max(a.size(), b.size());
        return max == 0 ? 1.0 : 1.0 - (double) editDistance(a, b) / max;
    }

    static double coverage(List<String> reference, List<String> mine) {
        if (reference.isEmpty()) {
            return 1.0;
        }
        Set<String> have = new LinkedHashSet<>(mine);
        long hit = new LinkedHashSet<>(reference).stream().filter(have::contains).count();
        return (double) hit / new LinkedHashSet<>(reference).size();
    }

    private static List<String> missing(List<String> reference, List<String> mine) {
        Set<String> have = new LinkedHashSet<>(mine);
        return new LinkedHashSet<>(reference).stream().filter(k -> !have.contains(k)).toList();
    }

    static List<String> dedupe(List<String> keys) {
        List<String> out = new ArrayList<>();
        for (String k : keys) {
            if (out.isEmpty() || !out.get(out.size() - 1).equals(k)) {
                out.add(k);
            }
        }
        return out;
    }

    static int editDistance(List<String> a, List<String> b) {
        int[][] d = new int[a.size() + 1][b.size() + 1];
        for (int i = 0; i <= a.size(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.size(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.size(); i++) {
            for (int j = 1; j <= b.size(); j++) {
                int cost = a.get(i - 1).equals(b.get(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
            }
        }
        return d[a.size()][b.size()];
    }
}
