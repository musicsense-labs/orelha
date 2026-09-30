package dev.musicsense.orelha.catalog;

import dev.musicsense.orelha.analysis.AnalysisRun;
import dev.musicsense.orelha.extraction.DataPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TrackRemovalTest {

    @TempDir
    Path tmp;

    private AnalysisRun run(Map<String, String> stems) {
        AnalysisRun run = new AnalysisRun(null, "orelha-extractor", null, Map.of());
        run.setStems(stems);
        return run;
    }

    @Test
    void audioAndStemsFolderAreListedOnce() {
        DataPaths data = new DataPaths("/data", tmp.resolve("data").toString());
        Path audio = tmp.resolve("audio/4/Creep.mp3");
        List<AnalysisRun> runs = List.of(
                run(Map.of("bass", "/data/stems/sha/bass.ogg", "vocals", "/data/stems/sha/vocals.ogg")),
                run(Map.of("bass", "/data/stems/sha/bass.ogg")),   // re-análise: mesma pasta
                run(null));                                         // run antigo, sem stems

        assertThat(TrackRemoval.filesOf(audio, runs, data)).containsExactly(
                audio,
                tmp.resolve("data/stems/sha").normalize());

        // Sem áudio (fora da biblioteca ou de outra faixa) e sem runs (bytes compartilhados): nada a apagar.
        assertThat(TrackRemoval.filesOf(null, List.of(), data)).isEmpty();
    }

    @Test
    void deleteRemovesFilesAndDirectoriesAndIgnoresMissingOnes() throws Exception {
        Path stems = Files.createDirectories(tmp.resolve("stems/sha"));
        Files.writeString(stems.resolve("bass.ogg"), "x");
        Path audio = Files.writeString(tmp.resolve("Creep.mp3"), "y");
        TrackRemoval.delete(Set.of(audio, stems, tmp.resolve("nao-existe.ogg")));
        assertThat(Files.exists(audio)).isFalse();
        assertThat(Files.exists(stems)).isFalse();
    }
}
