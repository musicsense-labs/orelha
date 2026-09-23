package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.Album;
import dev.musicsense.orelha.catalog.AlbumRepository;
import dev.musicsense.orelha.catalog.Artist;
import dev.musicsense.orelha.catalog.ArtistRepository;
import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O run que ficava preso em RUNNING quando o backend caía no meio da extração: a faixa não podia ser
 * excluída (409) e perdia o botão de reprocessar. Agora o worker bate o ponto enquanto trabalha e reclama
 * quem parou de bater — de volta à fila, ou FAILED depois de tentar demais.
 */
@SpringBootTest(properties = "orelha.worker.enabled=false")
@Testcontainers
class StaleRunReclaimIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    AnalysisQueue queue;
    @Autowired
    AnalysisRunRepository runs;
    @Autowired
    TrackRepository tracks;
    @Autowired
    AlbumRepository albums;
    @Autowired
    ArtistRepository artists;
    @Autowired
    TransactionTemplate tx;

    /** O banco exige 64 caracteres no SHA (V1); qualquer valor único serve para este teste. */
    private static String sha() {
        String unique = Long.toHexString(System.nanoTime()) + Long.toHexString(COUNTER++);
        return (unique + "0".repeat(64)).substring(0, 64);
    }

    private static long COUNTER = 0;

    private AnalysisRun runningSince(Instant lockedAt, int attempts) {
        return tx.execute(status -> {
            Artist artist = artists.save(new Artist("Stub " + System.nanoTime(), null, null));
            Album album = albums.save(new Album(artist, "Stub", 2026));
            Track track = tracks.save(new Track(album, "Stub", 1, "1/stub.wav", sha()));
            AnalysisRun run = runs.save(new AnalysisRun(track, "stub", null, java.util.Map.of()));
            run.setStatus(RunStatus.RUNNING);
            run.setAttempts(attempts);
            run.setLockedAt(lockedAt);
            run.setStartedAt(lockedAt);
            return runs.save(run);
        });
    }

    @Test
    void aRunWhoseHeartbeatStoppedGoesBackToTheQueue() {
        AnalysisRun abandoned = runningSince(Instant.now().minus(Duration.ofMinutes(30)), 1);

        assertThat(queue.reclaimStale(Duration.ofMinutes(2), 3)).isGreaterThanOrEqualTo(1);

        AnalysisRun after = runs.findById(abandoned.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RunStatus.QUEUED);
        assertThat(after.getLockedAt()).isNull();
        assertThat(after.getError()).isNull();
    }

    @Test
    void aRunStillBeatingIsLeftAlone() {
        AnalysisRun working = runningSince(Instant.now().minus(Duration.ofSeconds(20)), 1);

        queue.reclaimStale(Duration.ofMinutes(2), 3);

        assertThat(runs.findById(working.getId()).orElseThrow().getStatus()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void touchKeepsItAlive() {
        AnalysisRun working = runningSince(Instant.now().minus(Duration.ofMinutes(30)), 1);

        queue.touch(working.getId());   // o worker bateu o ponto agora
        queue.reclaimStale(Duration.ofMinutes(2), 3);

        assertThat(runs.findById(working.getId()).orElseThrow().getStatus()).isEqualTo(RunStatus.RUNNING);
    }

    @Test
    void afterTooManyAttemptsItFailsInsteadOfLooping() {
        AnalysisRun hopeless = runningSince(Instant.now().minus(Duration.ofHours(1)), 3);

        queue.reclaimStale(Duration.ofMinutes(2), 3);

        AnalysisRun after = runs.findById(hopeless.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(after.getError()).contains("Abandonado em execução");
        assertThat(after.getFinishedAt()).isNotNull();
    }
}
