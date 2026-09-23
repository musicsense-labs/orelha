package dev.musicsense.orelha.lyrics;

import dev.musicsense.orelha.catalog.AudioLibrary;
import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Traz para o banco o .lrc que está ao lado do áudio. Acontece quando a faixa entra no acervo e, para o que
 * já estava lá, por uma varredura sob demanda. É só leitura de arquivo: a fusão com o que o ASR ouviu é outra
 * coisa ({@link LyricMerger}), feita na leitura da letra.
 */
@Service
public class LrcImporter {

    private static final Logger log = LoggerFactory.getLogger(LrcImporter.class);

    /** O que a varredura encontrou. */
    public record Report(int imported, int lines, int alreadyHad, int emptyFile, int noFile) {
    }

    private final TrackRepository tracks;
    private final LrcLineRepository lines;
    private final AudioLibrary library;

    LrcImporter(TrackRepository tracks, LrcLineRepository lines, AudioLibrary library) {
        this.tracks = tracks;
        this.lines = lines;
        this.library = library;
    }

    /**
     * Lê o .lrc ao lado do áudio da faixa e grava os versos, trocando o que houvesse antes. Devolve quantos
     * versos entraram (0 quando não há arquivo ou ele não tem carimbo nenhum).
     */
    @Transactional
    public int importFor(Track track) {
        Path audio = library.resolve(track);
        Path lrc = LrcFile.besideAudio(audio);
        if (lrc == null) {
            return 0;
        }
        List<LrcFile.Line> parsed;
        try {
            parsed = LrcFile.read(lrc);
        } catch (RuntimeException e) {
            log.warn("faixa {}: não deu para ler {}: {}", track.getId(), lrc.getFileName(), e.getMessage());
            return 0;
        }
        if (parsed.isEmpty()) {
            return 0;   // arquivo sem carimbo: 21% dos baixados são assim
        }
        lines.deleteByTrackId(track.getId());
        List<LrcLine> rows = new ArrayList<>(parsed.size());
        for (int i = 0; i < parsed.size(); i++) {
            LrcFile.Line line = parsed.get(i);
            rows.add(new LrcLine(track, i, BigDecimal.valueOf(line.startS()), line.text()));
        }
        lines.saveAll(rows);
        log.info("faixa {}: {} versos de {}", track.getId(), rows.size(), lrc.getFileName());
        return rows.size();
    }

    /** Varre o acervo atrás de .lrc ao lado do áudio; {@code onlyMissing} pula quem já tem versos gravados. */
    @Transactional
    public Report scan(boolean onlyMissing) {
        int imported = 0;
        int totalLines = 0;
        int alreadyHad = 0;
        int emptyFile = 0;
        int noFile = 0;
        for (Track track : tracks.findAll()) {
            if (onlyMissing && lines.existsByTrackId(track.getId())) {
                alreadyHad++;
                continue;
            }
            Path lrc = LrcFile.besideAudio(library.resolve(track));
            if (lrc == null) {
                noFile++;
                continue;
            }
            int n = importFor(track);
            if (n > 0) {
                imported++;
                totalLines += n;
            } else {
                emptyFile++;
            }
        }
        log.info("varredura de .lrc: {} faixas, {} versos ({} já tinham, {} sem arquivo, {} arquivo vazio)",
                imported, totalLines, alreadyHad, noFile, emptyFile);
        return new Report(imported, totalLines, alreadyHad, emptyFile, noFile);
    }
}
