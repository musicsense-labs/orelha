package dev.musicsense.orelha.catalog;

import dev.musicsense.orelha.analysis.AnalysisRun;
import dev.musicsense.orelha.extraction.DataPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * O que apagar do disco quando uma faixa sai do acervo: o áudio (quando o chamador decidiu que é só dela),
 * a pasta de stems de cada run e o Parquet de features. Os caminhos são decididos na transação; a exclusão
 * em disco acontece depois do commit e nunca falha a operação — só avisa no log.
 */
public final class TrackRemoval {

    private static final Logger log = LoggerFactory.getLogger(TrackRemoval.class);

    private TrackRemoval() {
    }

    /**
     * Caminhos no host, sem repetição, na ordem: áudio (se houver), stems (pasta /data/stems/&lt;sha&gt;/ inteira),
     * features. {@code audio} null = o arquivo fica (fora da biblioteca, ou ainda de outra faixa).
     */
    public static Set<Path> filesOf(Path audio, List<AnalysisRun> runs, DataPaths dataPaths) {
        Set<Path> out = new LinkedHashSet<>();
        if (audio != null) {
            out.add(audio);
        }
        for (AnalysisRun run : runs) {
            if (run.getStems() != null) {
                for (String stem : run.getStems().values()) {
                    Path host = dataPaths.toHost(stem);
                    if (host != null && host.getParent() != null) {
                        out.add(host.getParent());
                    }
                }
            }
            Path features = dataPaths.toHost(run.getFeaturesPath());
            if (features != null) {
                out.add(features);
            }
        }
        return out;
    }

    /** Apaga arquivos e pastas (recursivo); o que não existir é ignorado, o que falhar vira aviso. */
    public static void delete(Set<Path> paths) {
        for (Path path : paths) {
            try {
                if (Files.isDirectory(path)) {
                    try (Stream<Path> walk = Files.walk(path)) {
                        for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(p);
                        }
                    }
                } else {
                    Files.deleteIfExists(path);
                }
            } catch (IOException | UncheckedIOException e) {   // Files.walk falha em unchecked
                log.warn("não apagou {}: {}", path, e.getMessage());
            }
        }
    }
}
