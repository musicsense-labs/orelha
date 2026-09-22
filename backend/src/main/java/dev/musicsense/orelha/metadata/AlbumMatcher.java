package dev.musicsense.orelha.metadata;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Quem é este álbum no MusicBrainz. Duas partes, ambas Java puro: limpar o título que veio das tags (rips de
 * box set trazem "Studio Albums Stereo Remastered Box Set (16CD) (2009) 05 - Help (6th August 1965)") e
 * pontuar os candidatos que a busca devolveu. A pontuação é conselho para o dono, não decisão: a UI mostra a
 * lista e ele confirma; o lote só aplica sozinho acima de {@link #CONFIDENT}.
 */
public final class AlbumMatcher {

    /** Acima disto o casamento é claro o bastante para o lote aplicar sem perguntar. */
    public static final double CONFIDENT = 0.85;

    /** Tipos de release-group que um acervo de estudo quer; o resto perde pontos. */
    private static final Set<String> PREFERRED_TYPES = Set.of("album", "ep");

    private AlbumMatcher() {
    }

    /**
     * O título provável do álbum dentro do nome que veio das tags: tira prefixo de box set ("… (16CD) (2009)
     * 05 - "), datas entre parênteses, marcas de disco e sufixos de remasterização.
     */
    public static String cleanTitle(String raw) {
        String s = raw == null ? "" : raw.strip();
        s = s.replaceAll("(?i)^.*?\\bbox\\s*set\\b.*?\\(\\d{4}\\)\\s*\\d*\\s*-?\\s*", "");   // tudo antes do álbum no box
        s = s.replaceAll("(?i)\\s*\\((?:\\d{1,2}(?:st|nd|rd|th)?\\s+)?[a-zç]*\\s*\\d{4}\\)\\s*", " ");  // (1965), (22nd March 1963)
        s = s.replaceAll("(?i)\\s*[\\[(]?\\b(?:\\d*\\s*cd\\d*|disc\\s*\\d+|cd\\s*\\d+)\\b[\\])]?\\s*", " ");
        s = s.replaceAll("(?i)\\s*[\\[(]?\\b(?:remaster(?:ed)?|stereo|mono|deluxe|expanded|anniversary|edition|version)\\b[\\])]?\\s*", " ");
        s = s.replaceAll("^\\s*\\d{1,2}\\s*[-–.]\\s*", "");   // "05 - Help"
        s = s.replaceAll("\\s{2,}", " ").replaceAll("[\\s\\-–]+$", "").strip();
        return s.isBlank() ? (raw == null ? "" : raw.strip()) : s;
    }

    /** Um candidato do MusicBrainz, já achatado pelo cliente. */
    public record Candidate(String mbid, String title, String artist, String primaryType, List<String> secondaryTypes,
                            Integer firstReleased, String disambiguation) {

        public Candidate {
            secondaryTypes = secondaryTypes == null ? List.of() : List.copyOf(secondaryTypes);
        }
    }

    public record Scored(Candidate candidate, double score) {
    }

    /**
     * Pontua cada candidato contra o que sabemos (título limpo e nome do artista), do melhor para o pior:
     * semelhança de título (peso 2), semelhança do artista (peso 1), bônus de tipo e de ter data.
     */
    public static List<Scored> rank(String rawTitle, String artistName, List<Candidate> candidates) {
        String title = fold(cleanTitle(rawTitle));
        String artist = fold(artistName);
        return candidates.stream()
                .map(c -> new Scored(c, score(title, artist, c)))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .toList();
    }

    /** Palavras de desambiguação que dizem "não é o álbum de estúdio". */
    private static final Set<String> NOT_THE_ALBUM = Set.of("live", "demo", "karaoke", "tribute", "instrumental", "remix");

    /**
     * Secondary-types que dizem "não é o disco original": coletânea, ao vivo, bootleg. Soundtrack fica de fora
     * de propósito — Help! e A Hard Day's Night são trilhas e continuam sendo os álbuns que queremos.
     */
    private static final Set<String> NOT_THE_ORIGINAL = Set.of("compilation", "live", "demo", "bootleg",
            "interview", "remix", "dj-mix", "mixtape/street", "audiobook", "spokenword");

    private static double score(String title, String artist, Candidate c) {
        double t = similarity(title, fold(c.title()));
        double a = nameSimilarity(artist, fold(c.artist()));
        double score = (2 * t + a) / 3;
        if (c.primaryType() == null || !PREFERRED_TYPES.contains(c.primaryType().toLowerCase(Locale.ROOT))) {
            score -= 0.15;   // Other, Single, Broadcast: não é o álbum
        } else {
            score += 0.05;
        }
        if (c.secondaryTypes().stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(NOT_THE_ORIGINAL::contains)) {
            score -= 0.25;   // "The Alternate Help!" e "Help Special" são coletâneas com o mesmo nome
        }
        if (c.firstReleased() != null) {
            score += 0.03;
        }
        String note = c.disambiguation() == null ? "" : c.disambiguation().toLowerCase(Locale.ROOT);
        if (!note.isBlank()) {
            score -= NOT_THE_ALBUM.stream().anyMatch(note::contains) ? 0.18 : 0.03;
        }
        // Artista é porta, não parcela: "Beatles Tribute Band" contém "Beatles" e não pode passar por eles.
        if (a < 0.85) {
            score *= a;
        }
        return Math.max(0, Math.min(1, score));
    }

    /** 1 − distância de edição normalizada; conter o outro vale menos quanto mais sobra ("Help!" × "Help! (live)"). */
    static double similarity(String a, String b) {
        return similarity(a, b, 0.55);
    }

    /** Para nome de artista a sobra pesa mais: "beatles" dentro de "beatles tribute band" não é o mesmo artista. */
    static double nameSimilarity(String a, String b) {
        return similarity(a, b, 0.5);
    }

    private static double similarity(String a, String b, double containmentFloor) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 1;
        }
        if (a.contains(b) || b.contains(a)) {
            double ratio = (double) Math.min(a.length(), b.length()) / Math.max(a.length(), b.length());
            return containmentFloor + (1 - containmentFloor) * ratio;
        }
        int distance = editDistance(a, b);
        return Math.max(0, 1 - (double) distance / Math.max(a.length(), b.length()));
    }

    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }

    /** Sem acento, sem caixa, sem pontuação e sem artigo inicial ("The Beatles" ≡ "beatles"). */
    static String fold(String text) {
        if (text == null) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s{2,}", " ").strip();
        return s.replaceFirst("^(the|a|os|as) ", "");
    }
}
