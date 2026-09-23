package dev.musicsense.orelha.lyrics;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Junta o que cada lado faz bem: o ASR sabe <em>quando</em> se canta (tempo por palavra, e sobretudo os
 * trechos em que não há canto — o que separa voz de solo vazado no stem); o .lrc sabe <em>o que</em> se canta
 * (texto humano, onde o Whisper erra feio em canto: "I wish out special" no lugar de "I wish I was special").
 *
 * <p>O casamento é por verso, não pela música inteira: cada verso do .lrc tem um carimbo de início, e as
 * palavras do ASR entre esse carimbo e o do verso seguinte formam a janela. Dentro da janela, um alinhamento
 * de edição (Needleman–Wunsch) decide palavra a palavra:
 *
 * <ul>
 *   <li>igual → mantém o tempo do ASR e o texto do .lrc (que traz pontuação e maiúsculas);</li>
 *   <li>diferente → <b>texto do .lrc com o tempo do ASR</b>: é a correção que interessa;</li>
 *   <li>só no .lrc (o ASR não ouviu) → entra interpolada entre as vizinhas;</li>
 *   <li>só no ASR (o .lrc não tem) → sai: é quase sempre alucinação sobre instrumental.</li>
 * </ul>
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

    /** Folga para pegar palavras que o ASR marcou um pouco antes do carimbo do verso. */
    private static final double WINDOW_SLACK_S = 1.5;

    /** Silêncio que fecha a janela do último verso (o .lrc não marca fim de linha). */
    private static final double LAST_WINDOW_SILENCE_S = 10;

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
        List<Word> asrWords = asr.stream().flatMap(s -> s.words().stream()).sorted((a, b) -> Double.compare(a.startS(), b.startS())).toList();
        List<Segment> out = new ArrayList<>(lrc.size());
        int corrected = 0;
        int inserted = 0;
        int kept = 0;
        int covered = 0;
        int dropped = 0;
        for (int i = 0; i < lrc.size(); i++) {
            double from = lrc.get(i).startS() - WINDOW_SLACK_S;
            double to = i + 1 < lrc.size() ? lrc.get(i + 1).startS() - WINDOW_SLACK_S : endOfLastWindow(asrWords, from);
            List<Word> window = asrWords.stream().filter(w -> w.startS() >= from && w.startS() < to).toList();
            covered += window.size();
            List<String> target = words(lrc.get(i).text());
            if (target.isEmpty()) {
                continue;
            }
            Merged merged = align(target, window, lrc.get(i).startS(), to);
            corrected += merged.corrected;
            inserted += merged.inserted;
            kept += merged.kept;
            dropped += merged.dropped;
            double start = merged.words.isEmpty() ? lrc.get(i).startS() : merged.words.get(0).startS();
            double end = merged.words.isEmpty() ? lrc.get(i).startS() : merged.words.get(merged.words.size() - 1).endS();
            out.add(new Segment(start, end, lrc.get(i).text(), merged.words));
        }
        dropped += asrWords.size() - covered;   // o que nem entrou numa janela é alucinação sobre instrumental
        return new Result(out, corrected, inserted, Math.max(0, dropped), kept);
    }

    private record Merged(List<Word> words, int corrected, int inserted, int kept, int dropped) {
    }

    /**
     * Onde termina a janela do último verso: depois dele o .lrc não diz mais nada, e uma alucinação lá adiante
     * não pode ser puxada para cá. Corta no primeiro silêncio longo do ASR.
     */
    private static double endOfLastWindow(List<Word> asrWords, double from) {
        double last = from;
        for (Word w : asrWords) {
            if (w.startS() < from) {
                continue;
            }
            if (w.startS() - last > LAST_WINDOW_SILENCE_S) {
                return last + LAST_WINDOW_SILENCE_S;
            }
            last = w.endS();
        }
        return Double.MAX_VALUE;
    }

    /** Needleman–Wunsch sobre as palavras normalizadas: mesma ordem, custo 1 por diferença. */
    private static Merged align(List<String> target, List<Word> heard, double lineStart, double lineEnd) {
        int n = target.size();
        int m = heard.size();
        int[][] cost = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) {
            cost[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            cost[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int same = fold(target.get(i - 1)).equals(fold(heard.get(j - 1).text())) ? 0 : 1;
                cost[i][j] = Math.min(Math.min(cost[i - 1][j] + 1, cost[i][j - 1] + 1), cost[i - 1][j - 1] + same);
            }
        }
        List<Word> words = new ArrayList<>(n);
        List<Integer> missingAt = new ArrayList<>();
        int corrected = 0;
        int kept = 0;
        int dropped = 0;
        int i = n;
        int j = m;
        while (i > 0) {
            boolean diagonal = j > 0
                    && cost[i][j] == cost[i - 1][j - 1] + (fold(target.get(i - 1)).equals(fold(heard.get(j - 1).text())) ? 0 : 1);
            if (diagonal) {
                Word heardWord = heard.get(j - 1);
                boolean same = fold(target.get(i - 1)).equals(fold(heardWord.text()));
                words.add(new Word(heardWord.startS(), heardWord.endS(), target.get(i - 1),
                        same ? heardWord.probability() : null));   // texto afirmado pelo .lrc não tem probabilidade
                if (same) {
                    kept++;
                } else {
                    corrected++;
                }
                i--;
                j--;
            } else if (j > 0 && cost[i][j] == cost[i][j - 1] + 1) {
                j--;   // o ASR ouviu algo que o verso não tem: alucinação, cai fora
                dropped++;
            } else {
                words.add(null);           // o .lrc tem palavra que o ASR não ouviu: tempo vem depois
                missingAt.add(words.size() - 1);
                i--;
            }
        }
        java.util.Collections.reverse(words);
        java.util.Set<Integer> gaps = new java.util.HashSet<>();
        for (int k = 0; k < words.size(); k++) {
            if (words.get(k) == null) {
                gaps.add(k);   // as que o ASR não ouviu: são elas que cedem se o tempo não crescer
            }
        }
        List<Word> result = monotonic(interpolate(words, target, lineStart, lineEnd), gaps);
        dropped += j;   // sobra antes do começo do verso: também não é dele
        return new Merged(result, corrected, missingAt.size(), kept, dropped);
    }

    /** Dá tempo às palavras que o ASR não ouviu, espalhando-as entre as vizinhas que têm tempo. */
    private static List<Word> interpolate(List<Word> words, List<String> target, double lineStart, double lineEnd) {
        int n = words.size();
        List<Word> out = new ArrayList<>(n);
        for (int k = 0; k < n; k++) {
            out.add(words.get(k));
        }
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
            int index = reverseIndex(words, k, target);
            out.set(k, new Word(round(start), round(start + step), target.get(index), null));
        }
        return out;
    }

    /**
     * O tempo tem de crescer com o texto: o ASR às vezes marca uma palavra antes do carimbo do verso e uma
     * palavra interpolada anterior acaba caindo depois dela (o verso sairia com "have" antes de "Can").
     * Quem cede é sempre a interpolada — o tempo que o ASR mediu é o dado, e não se mexe nele.
     */
    private static List<Word> monotonic(List<Word> words, java.util.Set<Integer> interpolated) {
        List<Word> out = new ArrayList<>(words);
        for (int k = 0; k < out.size(); k++) {          // para a frente: não começar antes da anterior
            if (!interpolated.contains(k) || k == 0) {
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

    /** As palavras saem na ordem do verso; o índice no alvo é a própria posição. */
    private static int reverseIndex(List<Word> words, int position, List<String> target) {
        return Math.min(position, target.size() - 1);
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
