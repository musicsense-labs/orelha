package dev.rifflab.corpus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Métricas de corpus sobre segmentos anotados. Java puro, sem I/O.
 * Toda distribuição sai em duas unidades (P3): por contagem de segmento e por duração.
 */
public final class CorpusMetrics {

    /** Grafia neutra dos 12 graus para agregados que misturam tonalidades maiores e menores. */
    public static final List<String> DEGREE_LABELS = List.of(
            "I", "♭II", "II", "♭III", "III", "IV", "♯IV/♭V", "V", "♭VI", "VI", "♭VII", "VII");

    private CorpusMetrics() {
    }

    /** Fração por contagem de segmentos e por duração. */
    public record Share(double bySegment, double byDuration) {
    }

    public record DegreeDistribution(double[] bySegment, double[] byDuration, double entropyBySegmentBits,
                                     double entropyByDurationBits) {
    }

    /** counts[from][to] sobre pares consecutivos com grau; rowNormalized é P(to | from). */
    public record TransitionMatrix(int[][] counts, double[][] rowNormalized, int total) {

        /** Matriz normalizada globalmente (soma 1), achatada: entrada das distâncias entre artistas. */
        public double[] flattened() {
            double[] p = new double[144];
            for (int i = 0; i < 12; i++) {
                for (int j = 0; j < 12; j++) {
                    p[i * 12 + j] = total == 0 ? 0 : (double) counts[i][j] / total;
                }
            }
            return p;
        }
    }

    public record PedalPassage(long trackId, String trackTitle, double startS, double endS, String fromLabel,
                               String toLabel, String relation, int bassPc) {
    }

    /** Eixo A sobre todos os segmentos (inclusive NONE). */
    public static Map<String, Share> keyRelationShares(List<AnnotatedSegment> segments) {
        return shares(segments, AnnotatedSegment::keyRelation);
    }

    /** Fração fora do campo harmônico entre segmentos com fundamental: tudo que não é DIATONIC nem AMBIGUOUS. */
    public static Share nonDiatonic(List<AnnotatedSegment> segments) {
        List<AnnotatedSegment> withDegree = segments.stream().filter(AnnotatedSegment::hasDegree).toList();
        Map<String, Share> shares = shares(withDegree, AnnotatedSegment::keyRelation);
        double inSeg = 0;
        double inDur = 0;
        for (String inside : List.of("DIATONIC", "AMBIGUOUS")) {
            Share s = shares.get(inside);
            if (s != null) {
                inSeg += s.bySegment();
                inDur += s.byDuration();
            }
        }
        return withDegree.isEmpty() ? new Share(0, 0) : new Share(1 - inSeg, 1 - inDur);
    }

    public static DegreeDistribution degrees(List<AnnotatedSegment> segments) {
        double[] bySegment = new double[12];
        double[] byDuration = new double[12];
        double n = 0;
        double total = 0;
        for (AnnotatedSegment s : segments) {
            if (s.hasDegree()) {
                bySegment[s.degreeInterval()]++;
                byDuration[s.degreeInterval()] += s.duration();
                n++;
                total += s.duration();
            }
        }
        normalize(bySegment, n);
        normalize(byDuration, total);
        return new DegreeDistribution(bySegment, byDuration, entropyBits(bySegment), entropyBits(byDuration));
    }

    public static TransitionMatrix transitions(List<AnnotatedSegment> segments) {
        int[][] counts = new int[12][12];
        int total = 0;
        AnnotatedSegment previous = null;
        for (AnnotatedSegment s : segments) {
            if (s.follows(previous) && previous.hasDegree() && s.hasDegree()) {
                counts[previous.degreeInterval()][s.degreeInterval()]++;
                total++;
            }
            previous = s;
        }
        double[][] rows = new double[12][12];
        for (int i = 0; i < 12; i++) {
            int rowTotal = 0;
            for (int c : counts[i]) {
                rowTotal += c;
            }
            for (int j = 0; j < 12; j++) {
                rows[i][j] = rowTotal == 0 ? 0 : (double) counts[i][j] / rowTotal;
            }
        }
        return new TransitionMatrix(counts, rows, total);
    }

    /** Eixo B sobre as transições; a duração é a do segmento de chegada. */
    public static Map<String, Share> relationShares(List<AnnotatedSegment> segments) {
        List<AnnotatedSegment> arrivals = new ArrayList<>();
        AnnotatedSegment previous = null;
        for (AnnotatedSegment s : segments) {
            if (s.follows(previous) && s.relationFromPrev() != null) {
                arrivals.add(s);
            }
            previous = s;
        }
        return shares(arrivals, AnnotatedSegment::relationFromPrev);
    }

    /**
     * Baixo parado enquanto a harmonia se move pela relação dada (pergunta 3 do Contexto):
     * pares consecutivos com a relação pedida e o mesmo baixo efetivo dos dois lados.
     */
    public static List<PedalPassage> pedalPassages(List<AnnotatedSegment> segments, String relation) {
        List<PedalPassage> passages = new ArrayList<>();
        AnnotatedSegment previous = null;
        for (AnnotatedSegment s : segments) {
            if (s.follows(previous) && relation.equals(s.relationFromPrev())
                    && s.effectiveBassPc() != null && s.effectiveBassPc().equals(previous.effectiveBassPc())) {
                passages.add(new PedalPassage(s.trackId(), s.trackTitle(), previous.startS(), s.endS(),
                        previous.degreeLabel(), s.degreeLabel(), relation, s.effectiveBassPc()));
            }
            previous = s;
        }
        return passages;
    }

    /** Entropia de Shannon em bits de uma distribuição (zeros ignorados). */
    public static double entropyBits(double[] p) {
        double h = 0;
        for (double v : p) {
            if (v > 0) {
                h -= v * log2(v);
            }
        }
        return h;
    }

    /** Divergência de Jensen-Shannon em bits: simétrica, 0 (iguais) a 1 (suportes disjuntos). */
    public static double jensenShannonBits(double[] p, double[] q) {
        double[] m = new double[p.length];
        for (int i = 0; i < p.length; i++) {
            m[i] = (p[i] + q[i]) / 2;
        }
        return (kl(p, m) + kl(q, m)) / 2;
    }

    public static double l1(double[] p, double[] q) {
        double d = 0;
        for (int i = 0; i < p.length; i++) {
            d += Math.abs(p[i] - q[i]);
        }
        return d;
    }

    /** L1 entre dois mapas de frações (chaves ausentes valem 0). */
    public static double l1(Map<String, Share> a, Map<String, Share> b) {
        TreeMap<String, Double> keys = new TreeMap<>();
        a.forEach((k, v) -> keys.merge(k, v.bySegment(), Double::sum));
        b.forEach((k, v) -> keys.merge(k, -v.bySegment(), Double::sum));
        return keys.values().stream().mapToDouble(Math::abs).sum();
    }

    private static Map<String, Share> shares(List<AnnotatedSegment> segments,
                                             java.util.function.Function<AnnotatedSegment, String> key) {
        Map<String, double[]> acc = new TreeMap<>();
        double n = 0;
        double total = 0;
        for (AnnotatedSegment s : segments) {
            String k = key.apply(s);
            if (k == null) {
                continue;
            }
            double[] v = acc.computeIfAbsent(k, x -> new double[2]);
            v[0]++;
            v[1] += s.duration();
            n++;
            total += s.duration();
        }
        Map<String, Share> out = new LinkedHashMap<>();
        for (Map.Entry<String, double[]> e : acc.entrySet()) {
            out.put(e.getKey(), new Share(n == 0 ? 0 : e.getValue()[0] / n, total == 0 ? 0 : e.getValue()[1] / total));
        }
        return out;
    }

    private static void normalize(double[] v, double total) {
        if (total > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= total;
            }
        }
    }

    private static double kl(double[] p, double[] m) {
        double d = 0;
        for (int i = 0; i < p.length; i++) {
            if (p[i] > 0) {
                d += p[i] * log2(p[i] / m[i]);
            }
        }
        return d;
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
