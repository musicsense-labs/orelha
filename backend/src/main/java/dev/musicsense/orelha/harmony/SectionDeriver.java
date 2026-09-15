package dev.musicsense.orelha.harmony;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Partes de uma música por repetição harmônica, sem nome de função (verso, refrão): só ordem e
 * recorrência são afirmáveis a partir dos acordes. Uma parte é um ciclo de compassos que se repete
 * ({@code C Am F G ×4}) ou um trecho sem repetição interna; trechos com a mesma harmonia recebem a
 * mesma letra (A, B, C…) em ordem de aparição. A grade é a de compassos (downbeats do extrator);
 * o acorde de cada compasso é o que o ocupa (≥ {@value #MIN_BAR_SHARE} do compasso), em ordem.
 * <p>
 * Java puro, sem I/O. Regras decididas em 2026-09-15: ciclo aceito só se cobrir ≥ {@value #MIN_CYCLE_BARS}
 * compassos (um acorde de dois compassos não vira parte); período máximo {@value #MAX_PERIOD} compassos;
 * empate por cobertura vai para o período menor; compassos sem acorde (N.C.) no início e no fim não
 * viram parte.
 */
public final class SectionDeriver {

    public static final int MAX_PERIOD = 8;
    public static final int MIN_CYCLE_BARS = 4;
    public static final double MIN_BAR_SHARE = 0.2;

    /** Um acorde no tempo; {@code id} é a identidade (fundamental + qualidade), {@code null} para N.C. */
    public record ChordSpan(double startS, double endS, String id) {
    }

    /** Uma parte: {@code cycleEndS} é o fim da primeira repetição do ciclo; {@code repeats} quantas vezes ele ocorre. */
    public record Section(double startS, double endS, double cycleEndS, int repeats, String label) {
    }

    private record Bar(double startS, double endS, String signature) {
    }

    private SectionDeriver() {
    }

    /**
     * @param chords    acordes em ordem, sem sobreposição
     * @param downbeats instantes de início de compasso, crescentes
     */
    public static List<Section> derive(List<ChordSpan> chords, List<Double> downbeats) {
        List<Bar> bars = bars(chords, downbeats);
        int from = 0;
        int to = bars.size();
        while (from < to && bars.get(from).signature() == null) {
            from++;
        }
        while (to > from && bars.get(to - 1).signature() == null) {
            to--;
        }
        if (from >= to) {
            return List.of();
        }

        List<Part> parts = new ArrayList<>();
        List<Bar> pending = new ArrayList<>();   // compassos sem ciclo, acumulados até o próximo ciclo
        int i = from;
        while (i < to) {
            Cycle cycle = bestCycle(bars, i, to);
            if (cycle == null) {
                pending.add(bars.get(i));
                i++;
                continue;
            }
            flush(parts, pending);
            List<Bar> cycleBars = bars.subList(i, i + cycle.period());
            parts.add(new Part(cycleBars.get(0).startS(), bars.get(i + cycle.period() * cycle.repeats() - 1).endS(),
                    cycleBars.get(cycle.period() - 1).endS(), cycle.repeats(), signature(cycleBars)));
            i += cycle.period() * cycle.repeats();
        }
        flush(parts, pending);

        Map<String, String> labels = new LinkedHashMap<>();
        List<Section> sections = new ArrayList<>(parts.size());
        for (Part part : parts) {
            String label = labels.computeIfAbsent(part.signature(), s -> letter(labels.size()));
            sections.add(new Section(part.startS(), part.endS(), part.cycleEndS(), part.repeats(), label));
        }
        return sections;
    }

    private record Part(double startS, double endS, double cycleEndS, int repeats, String signature) {
    }

    private record Cycle(int period, int repeats) {
    }

    private static void flush(List<Part> parts, List<Bar> pending) {
        if (pending.isEmpty()) {
            return;
        }
        double end = pending.get(pending.size() - 1).endS();
        parts.add(new Part(pending.get(0).startS(), end, end, 1, signature(pending)));
        pending.clear();
    }

    /** O ciclo que mais compassos cobre a partir de {@code i}; {@code null} se nada se repete o bastante. */
    private static Cycle bestCycle(List<Bar> bars, int i, int to) {
        Cycle best = null;
        int bestCoverage = 0;
        for (int p = 1; p <= MAX_PERIOD && i + 2 * p <= to; p++) {
            int k = 1;
            while (i + (k + 1) * p <= to && sameBars(bars, i, i + k * p, p)) {
                k++;
            }
            int coverage = k * p;
            if (k >= 2 && coverage >= MIN_CYCLE_BARS && coverage > bestCoverage) {
                best = new Cycle(p, k);
                bestCoverage = coverage;
            }
        }
        return best;
    }

    private static boolean sameBars(List<Bar> bars, int a, int b, int length) {
        for (int j = 0; j < length; j++) {
            if (!Objects.equals(bars.get(a + j).signature(), bars.get(b + j).signature())) {
                return false;
            }
        }
        return true;
    }

    private static String signature(List<Bar> bars) {
        StringBuilder sb = new StringBuilder();
        for (Bar bar : bars) {
            sb.append(bar.signature() == null ? "N" : bar.signature()).append('|');
        }
        return sb.toString();
    }

    /** Grade de compassos: os downbeats, mais um compasso de anacruse antes do primeiro e o resto após o último. */
    private static List<Bar> bars(List<ChordSpan> chords, List<Double> downbeats) {
        if (chords.isEmpty() || downbeats.isEmpty()) {
            return List.of();
        }
        double end = chords.get(chords.size() - 1).endS();
        List<Double> edges = new ArrayList<>();
        double first = downbeats.get(0);
        double typical = downbeats.size() > 1 ? (downbeats.get(downbeats.size() - 1) - first) / (downbeats.size() - 1) : first;
        if (first > 0.25 * typical && first > 0) {
            edges.add(0.0);
        }
        for (double d : downbeats) {
            if (d < end) {
                edges.add(d);
            }
        }
        edges.add(Math.max(end, edges.get(edges.size() - 1)));

        List<Bar> bars = new ArrayList<>();
        for (int b = 0; b + 1 < edges.size(); b++) {
            double start = edges.get(b);
            double stop = edges.get(b + 1);
            if (stop - start <= 0) {
                continue;
            }
            bars.add(new Bar(start, stop, barSignature(chords, start, stop)));
        }
        return bars;
    }

    /** Acordes que ocupam ≥ MIN_BAR_SHARE do compasso, em ordem, sem repetição consecutiva; null se nenhum. */
    private static String barSignature(List<ChordSpan> chords, double start, double stop) {
        double length = stop - start;
        StringBuilder sb = new StringBuilder();
        String last = null;
        for (ChordSpan c : chords) {
            if (c.endS() <= start || c.startS() >= stop) {
                continue;
            }
            double overlap = Math.min(c.endS(), stop) - Math.max(c.startS(), start);
            if (c.id() == null || overlap < MIN_BAR_SHARE * length) {
                continue;
            }
            if (!c.id().equals(last)) {
                sb.append(c.id()).append(' ');
                last = c.id();
            }
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    private static String letter(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }
}
