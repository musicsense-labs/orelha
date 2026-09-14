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
 * Metadados de um arquivo de áudio para o cadastro: tags (ID3, Vorbis, MP4, WAV) com fallback na
 * convenção de pastas {@code Artista/Álbum/01 Título.ext}. Sem teoria musical, só catálogo.
 */
public record AudioTags(String artist, String album, Integer year, String title, Integer trackNo, boolean fromTags) {

    private static final Pattern LEADING_TRACK_NO = Pattern.compile("^\\s*(\\d{1,3})\\s*[-._)\\s]+\\s*(.+)$");
    private static final Pattern LEADING_DIGITS = Pattern.compile("^\\s*(\\d+)");
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");

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
            // formato sem suporte ou arquivo sem tags: fica o fallback de pastas
        }
        String fileTitle = stripExtension(nominalPath.getFileName().toString());
        Matcher numbered = LEADING_TRACK_NO.matcher(fileTitle);
        Integer fileTrackNo = numbered.matches() ? Integer.valueOf(numbered.group(1)) : null;
        String fileBaseTitle = numbered.matches() ? numbered.group(2).strip() : fileTitle;

        Path parent = nominalPath.getParent();
        Path grandparent = parent == null ? null : parent.getParent();
        String folderAlbum = parent == null || parent.getFileName() == null ? null : parent.getFileName().toString();
        String folderArtist = grandparent == null || grandparent.getFileName() == null ? null : grandparent.getFileName().toString();

        String artist = firstNonBlank(field(tag, FieldKey.ALBUM_ARTIST), field(tag, FieldKey.ARTIST), folderArtist, "Desconhecido");
        String album = firstNonBlank(field(tag, FieldKey.ALBUM), folderAlbum, "Desconhecido");
        String title = firstNonBlank(field(tag, FieldKey.TITLE), fileBaseTitle);
        Integer trackNo = parseLeadingInt(field(tag, FieldKey.TRACK));
        boolean fromTags = tag != null && !isBlank(field(tag, FieldKey.TITLE));
        return new AudioTags(artist, album, parseYear(field(tag, FieldKey.YEAR)), title,
                trackNo != null ? trackNo : fileTrackNo, fromTags);
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
