package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.extraction.DataPaths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A faxina do disco: o que não pertence a faixa nenhuma sai, o resto fica. Os testes montam a árvore que o
 * extrator cria ({@code stems/<sha>/*.ogg} e {@code features/<sha>.parquet}) num diretório temporário.
 */
class OrphanSweeperTest {

    private static final String VIVO = "a".repeat(64);
    private static final String ORFAO = "b".repeat(64);

    @TempDir
    Path root;

    private TrackRepository tracks;

    @BeforeEach
    void setUp() throws IOException {
        tracks = Mockito.mock(TrackRepository.class);
        stems(VIVO);
        stems(ORFAO);
        parquet(VIVO);
        parquet(ORFAO);
    }

    private OrphanSweeper sweeper(Duration minAge) {
        return new OrphanSweeper(tracks, new DataPaths("/data", root.toString()), true, minAge);
    }

    private void stems(String sha) throws IOException {
        Path dir = Files.createDirectories(root.resolve("stems").resolve(sha));
        Files.writeString(dir.resolve("bass.ogg"), "bass");
        Files.writeString(dir.resolve("vocals.ogg"), "vocals");
        age(dir, Duration.ofHours(6));
    }

    private void parquet(String sha) throws IOException {
        Files.createDirectories(root.resolve("features"));
        Path file = Files.writeString(root.resolve("features").resolve(sha + ".parquet"), "parquet");
        age(file, Duration.ofHours(6));
    }

    /** Envelhece a entrada (e o que ela contém) para além da idade mínima. */
    private static void age(Path path, Duration by) throws IOException {
        FileTime when = FileTime.from(Instant.now().minus(by));
        if (Files.isDirectory(path)) {
            try (var walk = Files.walk(path)) {
                for (Path p : walk.toList()) {
                    Files.setLastModifiedTime(p, when);
                }
            }
        } else {
            Files.setLastModifiedTime(path, when);
        }
    }

    @Test
    void onlyWhatBelongsToNoTrackIsDeleted() {
        Mockito.when(tracks.findAllAudioSha256()).thenReturn(Set.of(VIVO));

        OrphanSweeper.Report report = sweeper(Duration.ofHours(1)).sweep(false);

        assertThat(report.stemFolders()).isEqualTo(1);
        assertThat(report.featureFiles()).isEqualTo(1);
        assertThat(report.bytes()).isGreaterThan(0);
        assertThat(Files.exists(root.resolve("stems").resolve(VIVO))).isTrue();
        assertThat(Files.exists(root.resolve("features").resolve(VIVO + ".parquet"))).isTrue();
        assertThat(Files.exists(root.resolve("stems").resolve(ORFAO))).isFalse();
        assertThat(Files.exists(root.resolve("features").resolve(ORFAO + ".parquet"))).isFalse();
    }

    @Test
    void aDryRunCountsAndKeepsEverything() {
        Mockito.when(tracks.findAllAudioSha256()).thenReturn(Set.of(VIVO));

        OrphanSweeper.Report report = sweeper(Duration.ofHours(1)).sweep(true);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.stemFolders()).isEqualTo(1);
        assertThat(Files.exists(root.resolve("stems").resolve(ORFAO))).isTrue();
    }

    @Test
    void anEmptyCollectionAbortsTheSweep() {
        Mockito.when(tracks.findAllAudioSha256()).thenReturn(Set.of());   // banco fora do ar, catálogo zerado

        OrphanSweeper.Report report = sweeper(Duration.ofHours(1)).sweep(false);

        assertThat(report.skipped()).isTrue();
        assertThat(report.skippedReason()).contains("vazio");
        assertThat(Files.exists(root.resolve("stems").resolve(ORFAO))).isTrue();
    }

    @Test
    void whatWasJustWrittenIsLeftAloneEvenIfItLooksOrphan() throws IOException {
        Mockito.when(tracks.findAllAudioSha256()).thenReturn(Set.of(VIVO));
        // Uma análise em andamento: a faixa ainda não existe para esta varredura, mas os arquivos são de agora.
        String emAndamento = "c".repeat(64);
        stems(emAndamento);
        age(root.resolve("stems").resolve(emAndamento), Duration.ZERO);

        sweeper(Duration.ofHours(1)).sweep(false);

        assertThat(Files.exists(root.resolve("stems").resolve(emAndamento))).isTrue();
        assertThat(Files.exists(root.resolve("stems").resolve(ORFAO))).isFalse();
    }

    @Test
    void foreignFilesInTheFeaturesFolderAreNotTouched() throws IOException {
        Mockito.when(tracks.findAllAudioSha256()).thenReturn(Set.of(VIVO));
        Path foreign = Files.writeString(root.resolve("features").resolve("anotacoes.txt"), "não é nosso");
        age(foreign, Duration.ofDays(30));

        sweeper(Duration.ofHours(1)).sweep(false);

        assertThat(Files.exists(foreign)).isTrue();
    }
}
