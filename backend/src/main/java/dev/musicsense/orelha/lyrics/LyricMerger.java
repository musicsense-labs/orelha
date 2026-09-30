package dev.musicsense.orelha.lyrics;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Junta o que cada lado faz bem: o ASR sabe <em>quando</em> se canta (tempo por palavra, e sobretudo os
 * trechos em que não há canto — o que separa voz de solo vazado no stem); o .lrc sabe <em>o que</em> se canta
 * (texto humano, onde o Whisper erra feio em canto: "I wish out special" no lugar de "I wish I was special").
 *
 * <p>Um alinhamento de edição (Needleman–Wunsch) sobre a música inteira — todas as palavras do .lrc contra
 * todas as do ASR — decide palavra a palavra:
 *
 * <ul>
 *   <li>igual → mantém o tempo do ASR e o texto do .lrc (que traz pontuação e maiúsculas);</li>
 *   <li>diferente → <b>texto do .lrc com o tempo do ASR</b>: é a correção que interessa;</li>
 *   <li>só no .lrc (o ASR não ouviu) → entra interpolada entre as vizinhas do mesmo verso;</li>
 *   <li>só no ASR (o .lrc não tem) → sai: é quase sempre alucinação sobre instrumental.</li>
 * </ul>
 *
 * <p>O carimbo de cada verso é uma <em>restrição</em>, não uma janela: uma palavra do .lrc só casa com uma
 * palavra ouvida entre o carimbo do seu verso e o do seguinte, com folga de {@link #SLACK_S} para os dois
 * lados. As zonas de folga se sobrepõem de propósito, e quem decide a que verso vai uma palavra da fronteira é
 * o alinhamento — o casamento exato vence. Com janelas rígidas (até 2026-09-30), a cauda de um verso caía na
 * janela do seguinte e o alinhamento trocava palavra por palavra: em Let It Be, "Mother" ficava com o tempo de
 * "times" e o fim de cada verso virava vazamento no piano roll.
 *
 * <p>Java puro, sem dependência de banco: o que entra e o que sai são listas.
 */
public final class LyricMerger {

    /** Uma palavra com tempo; {@code probability} nula quando não veio do ASR. */
    public record Word(double startS, double endS, String text, Double probability) {
    }

    /** Um verso pronto para a tela: tempo, texto e as palavras com seus tempos. */
    public record Segment(double startS, double endS, String text, List<Word> words) {
    }

    /** Quanto o casamento mudou, para a tela poder dizer de onde veio cada coisa. */
    public record Result(List<Segment> segments, int corrected, int inserted, int dropped, int kept) {

        public boolean changedAnything() {
            return corrected > 0 || inserted > 0 || dropped > 0;
        }
    }

    /** Folga em volta do verso: o ASR marca palavras um pouco antes do carimbo, e a cauda passa do próximo. */
    private static final double SLACK_S = 1.5;

    /** Silêncio que fecha o último verso (o .lrc não marca fim de linha). */
    private static final double LAST_WINDOW_SILENCE_S = 10;

    private static final int FORBIDDEN = Integer.MAX_VALUE / 4;

    private LyricMerger() {
    }

    /**
     * @param asr   versos do ASR, em ordem, com as palavras de cada um
     * @param lrc   versos do .lrc, em ordem de tempo
     */
    public static Result merge(List<Segment> asr, List<LrcFile.Line> lrc) {
        if (lrc.isEmpty()) {
            return new Result(asr, 0, 0, 0, asr.stream().mapToInt(s -> s.words().size()).sum());
        }
        List<Word> heard = asr.stream().flatMap(s -> s.words().stream())
                .sorted((a, b) -> Double.compare(a.startS(), b.startS())).toList();

        // Todas as palavras do .lrc em sequência, cada uma sabendo o seu verso.
        List<String> target = new ArrayList<>();
        List<Integer> lineOf = new ArrayList<>();
        for (int i = 0; i < lrc.size(); i++) {
            for (String w : words(lrc.get(i).text())) {
                target.add(w);
                lineOf.add(i);
            }
        }
        double[] lineEnd = new double[lrc.size()];
        for (int i = 0; i < lrc.size(); i++) {
            lineEnd[i] = i + 1 < lrc.size() ? lrc.get(i + 1).startS() : endOfLastLine(heard, lrc.get(i).startS() - SLACK_S);
        }

        Alignment alignment = align(target, lineOf, heard, lrc, lineEnd);

        List<Segment> out = new ArrayList<>(lrc.size());
        int from = 0;
        for (int i = 0; i < lrc.size(); i++) {
            int to = from;
            while (to < target.size() && lineOf.get(to) == i) {
                to++;
            }
            if (to > from) {
                List<Word> words = new ArrayList<>(alignment.assigned.subList(from, to));
                Set<Integer> gaps = new HashSet<>();
                for (int k = 0; k < words.size(); k++) {
                    if (words.get(k) == null) {
                        gaps.add(k);   // as que o ASR não ouviu: são elas que cedem se o tempo não crescer
                    }
                }
                List<Word> timed = monotonic(interpolate(words, target.subList(from, to), lrc.get(i).startS(), lineEnd[i]), gaps);
                out.add(new Segment(timed.get(0).startS(), timed.get(timed.size() - 1).endS(), lrc.get(i).text(), timed));
            }
            from = to;
        }
        return new Result(out, alignment.corrected, alignment.inserted, alignment.dropped, alignment.kept);
    }

    /** Palavra do .lrc → palavra ouvida que lhe dá o tempo (null = o ASR não ouviu), e as contagens. */
    private record Alignment(List<Word> assigned, int corrected, int inserted, int dropped, int kept) {
    }

    /**
     * Needleman–Wunsch com custo 1 por diferença e a restrição de tempo: casar (igual ou trocada) a palavra
     * {@code k} do .lrc com a ouvida {@code j} só vale se {@code j} cai no verso de {@code k}, com a folga.
     */
    private static Alignment align(List<String> target, List<Integer> lineOf, List<Word> heard,
                                   List<LrcFile.Line> lrc, double[] lineEnd) {
        int n = target.size();
        int m = heard.size();
        String[] t = target.stream().map(LyricMerger::fold).toArray(String[]::new);
        String[] h = heard.stream().map(w -> fold(w.text())).toArray(String[]::new);
        // pair[k][j]: custo de casar a palavra k do .lrc com a ouvida j (0 igual, 1 trocada, proibido fora do verso)
        int[][] cost = new int[n + 1][m + 1];
        int[][] pair = new int[n + 1][m + 1];
        for (int k = 0; k <= n; k++) {
            cost[k][0] = k;
        }
        for (int j = 0; j <= m; j++) {
            cost[0][j] = j;
        }
        for (int k = 1; k <= n; k++) {
            int line = lineOf.get(k - 1);
            double earliest = lrc.get(line).startS() - SLACK_S;
            double latest = lineEnd[line] + SLACK_S;
            for (int j = 1; j <= m; j++) {
                double at = heard.get(j - 1).startS();
                pair[k][j] = at >= earliest && at < latest ? cost[k - 1][j - 1] + (t[k - 1].equals(h[j - 1]) ? 0 : 1) : FORBIDDEN;
                cost[k][j] = Math.min(pair[k][j], Math.min(cost[k - 1][j], cost[k][j - 1]) + 1);
            }
        }

        Word[] assigned = new Word[n];
        int corrected = 0;
        int kept = 0;
        int dropped = 0;
        int inserted = 0;
        int k = n;
        int j = m;
        while (k > 0 || j > 0) {
            if (k > 0 && j > 0 && cost[k][j] == pair[k][j]) {
                Word w = heard.get(j - 1);
                boolean same = t[k - 1].equals(h[j - 1]);
                // texto afirmado pelo .lrc não tem probabilidade; o que o ASR acertou mantém a dele
                assigned[k - 1] = new Word(w.startS(), w.endS(), target.get(k - 1), same ? w.probability() : null);
                if (same) {
                    kept++;
                } else {
                    corrected++;
                }
                k--;
                j--;
            } else if (j > 0 && cost[k][j] == cost[k][j - 1] + 1) {
                dropped++;   // o ASR ouviu algo que o .lrc não tem: alucinação, cai fora
                j--;
            } else {
                inserted++;  // o .lrc tem palavra que o ASR não ouviu: tempo vem depois
                k--;
            }
        }
        return new Alignment(Arrays.asList(assigned), corrected, inserted, dropped, kept);
    }

    /**
     * Onde termina o último verso: depois dele o .lrc não diz mais nada, e uma alucinação lá adiante não pode
     * ser puxada para cá. Corta no primeiro silêncio longo do ASR.
     */
    private static double endOfLastLine(List<Word> heard, double from) {
        double last = from;
        for (Word w : heard) {
            if (w.startS() < from) {
                continue;
            }
            if (w.startS() - last > LAST_WINDOW_SILENCE_S) {
                return last + LAST_WINDOW_SILENCE_S;
            }
            last = w.endS();
        }
        return Math.max(last, from + SLACK_S);
    }

    /** Dá tempo às palavras que o ASR não ouviu, espalhando-as entre as vizinhas que têm tempo. */
    private static List<Word> interpolate(List<Word> words, List<String> target, double lineStart, double lineEnd) {
        int n = words.size();
        List<Word> out = new ArrayList<>(words);
        for (int k = 0; k < n; k++) {
            if (out.get(k) != null) {
                continue;
            }
            int before = k - 1;
            while (before >= 0 && out.get(before) == null) {
                before--;
            }
            int after = k + 1;
            while (after < n && out.get(after) == null) {
                after++;
            }
            double from = before >= 0 ? out.get(before).endS() : lineStart;
            double to = after < n ? out.get(after).startS() : Math.min(lineEnd, from + 2);
            if (!(to > from)) {
                to = from + 0.3;
            }
            int gaps = after - before;                       // quantas palavras sem tempo há no buraco
            double step = (to - from) / Math.max(1, gaps);
            double start = from + step * (k - before - 1);
            out.set(k, new Word(round(start), round(start + step), target.get(k), null));
        }
        return out;
    }

    /**
     * O tempo tem de crescer com o texto: o ASR às vezes marca uma palavra antes do carimbo do verso e uma
     * palavra interpolada anterior acaba caindo depois dela (o verso sairia com "have" antes de "Can").
     * Quem cede é sempre a interpolada — o tempo que o ASR mediu é o dado, e não se mexe nele.
     */
    private static List<Word> monotonic(List<Word> words, Set<Integer> interpolated) {
        List<Word> out = new ArrayList<>(words);
        for (int k = 1; k < out.size(); k++) {          // para a frente: não começar antes da anterior
            if (!interpolated.contains(k)) {
                continue;
            }
            double floor = out.get(k - 1).startS() + 0.01;
            if (out.get(k).startS() < floor) {
                out.set(k, shifted(out.get(k), floor));
            }
        }
        for (int k = out.size() - 2; k >= 0; k--) {     // para trás: não invadir a seguinte
            if (!interpolated.contains(k)) {
                continue;
            }
            double ceiling = out.get(k + 1).startS() - 0.01;
            if (out.get(k).startS() > ceiling) {
                out.set(k, shifted(out.get(k), Math.max(0, ceiling)));
            }
        }
        return out;
    }

    private static Word shifted(Word w, double start) {
        double length = Math.max(0.05, w.endS() - w.startS());
        return new Word(round(start), round(start + length), w.text(), w.probability());
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    static List<String> words(String text) {
        List<String> out = new ArrayList<>();
        for (String part : text.split("\\s+")) {
            String clean = part.strip();
            if (!clean.isEmpty()) {
                out.add(clean);
            }
        }
        return out;
    }

    /** Comparação sem acento, caixa ou pontuação: "Cry," e "cry" são a mesma palavra. */
    static String fold(String word) {
        String s = Normalizer.normalize(word, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return s.replaceAll("[^\\p{Alnum}]", "");
    }
}
