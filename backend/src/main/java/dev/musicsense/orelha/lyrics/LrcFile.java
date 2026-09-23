package dev.musicsense.orelha.lyrics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitor de arquivo .lrc — a letra sincronizada que vem ao lado do áudio. Formato de fato: uma linha por
 * verso, começando por um ou mais carimbos {@code [mm:ss.xx]}; tags de metadado ({@code [ti:]}, {@code [ar:]})
 * e linhas sem carimbo são ignoradas. O carimbo marca só o <em>início</em> do verso — não há fim, e é por
 * isso que o LRC sozinho não diz onde o canto para.
 */
public final class LrcFile {

    /** Um verso: quando começa e o que se canta. */
    public record Line(double startS, String text) {
    }

    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private LrcFile() {
    }

    /** O .lrc ao lado do áudio ({@code Creep.mp3} → {@code Creep.lrc}), ou null se não existe. */
    public static Path besideAudio(Path audio) {
        String name = audio.getFileName().toString();
        int dot = name.lastIndexOf('.');
        Path lrc = audio.resolveSibling((dot < 0 ? name : name.substring(0, dot)) + ".lrc");
        return Files.isRegularFile(lrc) ? lrc : null;
    }

    /** Versos em ordem de tempo; lista vazia para arquivo sem carimbo nenhum (21% dos baixados estão assim). */
    public static List<Line> read(Path lrc) {
        return parse(readText(lrc));
    }

    public static List<Line> parse(String content) {
        List<Line> out = new ArrayList<>();
        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            List<Double> stamps = new ArrayList<>();
            Matcher m = STAMP.matcher(line);
            int textFrom = 0;
            while (m.find() && m.start() == textFrom) {   // só os carimbos do começo da linha
                stamps.add(seconds(m.group(1), m.group(2), m.group(3)));
                textFrom = m.end();
            }
            if (stamps.isEmpty()) {
                continue;   // tag de metadado ou linha solta
            }
            String text = line.substring(textFrom).strip();
            if (text.isEmpty()) {
                continue;   // carimbo sem letra: marcação de instrumental, não é verso
            }
            for (double at : stamps) {   // o mesmo verso pode ter vários carimbos (refrão repetido)
                out.add(new Line(at, text));
            }
        }
        out.sort(Comparator.comparingDouble(Line::startS));   // carimbos repetidos saem fora de ordem
        return out;
    }

    private static double seconds(String minutes, String secs, String fraction) {
        double value = Integer.parseInt(minutes) * 60 + Integer.parseInt(secs);
        if (fraction != null) {
            value += Double.parseDouble("0." + fraction);
        }
        return Math.round(value * 1000) / 1000.0;
    }

    /** Os arquivos vêm em UTF-8 quase sempre; alguns antigos em Latin-1, e um acento não pode derrubar o import. */
    private static String readText(Path lrc) {
        try {
            return Files.readString(lrc, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            try {
                return Files.readString(lrc, StandardCharsets.ISO_8859_1);
            } catch (IOException second) {
                throw new UncheckedIOException(second);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
