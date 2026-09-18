package dev.musicsense.orelha.practice;

import java.util.ArrayList;
import java.util.List;

/**
 * Escolhe corda e casa para cada nota de uma linha de baixo (tablatura). A altura sozinha é ambígua — Lá2
 * cabe em quatro lugares num baixo de 4 cordas —, então a escolha é feita sobre a sequência inteira por
 * programação dinâmica (Viterbi): a digitação de menor custo acumulado, onde o custo de ir de uma posição
 * à seguinte é a distância de casas (deslocamento da mão), a troca de corda, a altura da casa (acima da 12ª
 * penaliza) e a preferência por corda solta. Corda solta não move a mão, então entrar ou sair dela custa
 * zero deslocamento — aproximação: depois de uma solta o algoritmo não lembra onde a mão estava, e a
 * penalidade de casa alta e a de troca de corda desempatam. Notas fora do braço sobem ou descem de oitava
 * até caber (o basic-pitch erra a oitava em graves) e saem marcadas.
 */
public final class TabArranger {

    /** Afinação padrão de 4 cordas, grave → aguda: E1 A1 D2 G2. */
    public static final int[] STANDARD_4 = {28, 33, 38, 43};
    public static final int DEFAULT_MAX_FRET = 24;

    /** Uma nota a posicionar; {@code startS} só entra para aliviar o deslocamento quando há tempo de mover a mão. */
    public record Note(double startS, int midi) {
    }

    /** Corda 0 = a mais grave; {@code fret} 0 = solta; {@code octaveShifted} = a nota original não cabia no braço. */
    public record Position(int string, int fret, boolean octaveShifted) {
    }

    private final int[] tuning;
    private final int maxFret;
    private final boolean preferOpen;

    public TabArranger(int[] tuning, int maxFret, boolean preferOpen) {
        this.tuning = tuning.clone();
        this.maxFret = maxFret;
        this.preferOpen = preferOpen;
    }

    public static TabArranger standard4(boolean preferOpen) {
        return new TabArranger(STANDARD_4, DEFAULT_MAX_FRET, preferOpen);
    }

    public int[] tuning() {
        return tuning.clone();
    }

    /** Uma posição por nota, na mesma ordem. Lista vazia devolve lista vazia. */
    public List<Position> arrange(List<Note> notes) {
        int n = notes.size();
        List<Position> out = new ArrayList<>(n);
        if (n == 0) {
            return out;
        }
        List<List<Position>> candidates = new ArrayList<>(n);
        for (Note note : notes) {
            candidates.add(candidatesOf(note.midi()));
        }
        // Viterbi: custo acumulado e ponteiro para o melhor antecessor de cada candidata.
        double[][] cost = new double[n][];
        int[][] back = new int[n][];
        for (int i = 0; i < n; i++) {
            List<Position> cs = candidates.get(i);
            cost[i] = new double[cs.size()];
            back[i] = new int[cs.size()];
            for (int c = 0; c < cs.size(); c++) {
                Position cur = cs.get(c);
                double local = localCost(cur);
                if (i == 0) {
                    cost[i][c] = local;
                    back[i][c] = -1;
                    continue;
                }
                double gap = notes.get(i).startS() - notes.get(i - 1).startS();
                double best = Double.MAX_VALUE;
                int bestPrev = 0;
                List<Position> prevs = candidates.get(i - 1);
                for (int p = 0; p < prevs.size(); p++) {
                    double total = cost[i - 1][p] + transitionCost(prevs.get(p), cur, gap);
                    if (total < best) {
                        best = total;
                        bestPrev = p;
                    }
                }
                cost[i][c] = best + local;
                back[i][c] = bestPrev;
            }
        }
        int c = argmin(cost[n - 1]);
        Position[] path = new Position[n];
        for (int i = n - 1; i >= 0; i--) {
            path[i] = candidates.get(i).get(c);
            c = back[i][c];
        }
        out.addAll(List.of(path));
        return out;
    }

    /** Todas as (corda, casa) que produzem a altura; fora do braço, desloca de oitava até caber. */
    List<Position> candidatesOf(int midi) {
        int lowest = tuning[0];
        int highest = tuning[tuning.length - 1] + maxFret;
        boolean shifted = false;
        while (midi < lowest) {
            midi += 12;
            shifted = true;
        }
        while (midi > highest) {
            midi -= 12;
            shifted = true;
        }
        List<Position> out = new ArrayList<>(tuning.length);
        for (int s = 0; s < tuning.length; s++) {
            int fret = midi - tuning[s];
            if (fret >= 0 && fret <= maxFret) {
                out.add(new Position(s, fret, shifted));
            }
        }
        return out;
    }

    /** Custo da posição em si: casa alta é desconfortável; solta é bônus (ou penalidade, sem preferência). */
    private double localCost(Position p) {
        double cost = Math.max(0, p.fret() - 12) * 0.5;
        if (p.fret() == 0) {
            cost += preferOpen ? -0.5 : 1.0;
        }
        return cost;
    }

    /** Custo de ir de uma posição à seguinte: deslocamento da mão (aliviado quando há tempo) e troca de corda. */
    private static double transitionCost(Position prev, Position cur, double gapS) {
        double move = 0;
        if (prev.fret() > 0 && cur.fret() > 0) {
            int distance = Math.abs(cur.fret() - prev.fret());
            move = distance <= 4 ? distance * 0.5 : distance;   // até 4 casas é abertura de mão, não deslocamento
            if (gapS > 0.75) {
                move *= 0.25;                                    // deu tempo de mover
            }
        }
        double stringChange = Math.abs(cur.string() - prev.string()) * 0.3;
        return move + stringChange;
    }

    private static int argmin(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[best]) {
                best = i;
            }
        }
        return best;
    }
}
