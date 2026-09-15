package dev.rifflab.catalog;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Metadados de um arquivo de áudio para o cadastro: tags (ID3, Vorbis, MP4, WAV) com fallback no
 * nome e nas pastas. Sem teoria musical, só catálogo.
 *
 * <p>Fallback sem tags: {@code Artista/Álbum/01 Título.ext} pelas pastas; se o nome for
 * {@code "Artista - Título"}, o artista vem do nome (a UI permite trocar); sufixos entre parênteses
 * ou colchetes no fim do nome ({@code (youtube)}, {@code [Official Video]}) são descartados.
 */
public record AudioTags(String artist, String album, Integer year, String title, Integer trackNo, boolean fromTags) {

    private static final Pattern LEADING_TRACK_NO = Pattern.compile("^\\s*(\\d{1,3})\\s*[-._)\\s]+\\s*(.+)$");
    private static final Pattern LEADING_DIGITS = Pattern.compile("^\\s*(\\d+)");
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");
    private static final Pattern TRAILING_BRACKETS = Pattern.compile("\\s*[(\\[][^()\\[\\]]*[)\\]]\\s*$");
    private static final Pattern ARTIST_DASH_TITLE = Pattern.compile("^(.+?)\\s+[-–—]\\s+(.+)$");

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    public static AudioTags read(Path file) {
        return read(file, file);
    }

    /**
     * @param content     arquivo cujo conteúdo (tags) é lido
     * @param nominalPath caminho usado no fallback de pastas e nome — o próprio arquivo, ou o caminho
     *                    relativo que o navegador enviou num upload de pasta
     */
    public static AudioTags read(Path content, Path nominalPath) {
        Tag tag = null;
        try {
            tag = AudioFileIO.read(content.toFile()).getTag();
        } catch (Exception ignored) {
            // formato sem suporte ou arquivo sem tags: fica o fallback
        }
        FileNameGuess guess = FileNameGuess.of(nominalPath);

        String artist = firstNonBlank(field(tag, FieldKey.ALBUM_ARTIST), field(tag, FieldKey.ARTIST), guess.artist,
                guess.folderArtist, "Desconhecido");
        String album = firstNonBlank(field(tag, FieldKey.ALBUM), guess.folderAlbum, "Desconhecido");
        String title = firstNonBlank(field(tag, FieldKey.TITLE), guess.title);
        Integer trackNo = parseLeadingInt(field(tag, FieldKey.TRACK));
        boolean fromTags = tag != null && !isBlank(field(tag, FieldKey.TITLE));
        return new AudioTags(artist, album, parseYear(field(tag, FieldKey.YEAR)), title,
                trackNo != null ? trackNo : guess.trackNo, fromTags);
    }

    /** O que dá para tirar só do caminho: "Artista/Álbum/01 Artista - Título (youtube).mp3". */
    record FileNameGuess(String folderArtist, String folderAlbum, String artist, String title, Integer trackNo) {

        static FileNameGuess of(Path nominalPath) {
            String name = stripExtension(nominalPath.getFileName().toString());
            Matcher numbered = LEADING_TRACK_NO.matcher(name);
            Integer trackNo = numbered.matches() ? Integer.valueOf(numbered.group(1)) : null;
            String rest = cleanTitle(numbered.matches() ? numbered.group(2) : name);

            String artist = null;
            String title = rest;
            Matcher dash = ARTIST_DASH_TITLE.matcher(rest);
            if (dash.matches()) {
                artist = dash.group(1).strip();
                title = dash.group(2).strip();
            }

            Path parent = nominalPath.getParent();
            Path grandparent = parent == null ? null : parent.getParent();
            String folderAlbum = parent == null || parent.getFileName() == null ? null : parent.getFileName().toString();
            String folderArtist = grandparent == null || grandparent.getFileName() == null ? null : grandparent.getFileName().toString();
            return new FileNameGuess(folderArtist, folderAlbum, artist, title, trackNo);
        }
    }

    /** Remove sufixos entre parênteses/colchetes no fim: "Creep (Remastered) [Audio]" → "Creep". */
    static String cleanTitle(String raw) {
        String s = raw.strip();
        while (true) {
            Matcher m = TRAILING_BRACKETS.matcher(s);
            if (!m.find() || m.start() == 0) {
                return s;
            }
            s = s.substring(0, m.start()).strip();
        }
    }

    private static String field(Tag tag, FieldKey key) {
        if (tag == null) {
            return null;
        }
        try {
            String value = tag.getFirst(key);
            return value == null ? null : value.strip();
        } catch (Exception e) {
            return null;
        }
    }

    /** "3/12" → 3. */
    static Integer parseLeadingInt(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        Matcher m = LEADING_DIGITS.matcher(raw);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    /** "1970-02-13" ou "1970" → 1970. */
    static Integer parseYear(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        Matcher m = YEAR.matcher(raw);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) {
                return v.strip();
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
