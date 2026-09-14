package dev.rifflab.analysis;

import dev.rifflab.catalog.AlbumRequest;
import dev.rifflab.catalog.AlbumResponse;
import dev.rifflab.catalog.ArtistRequest;
import dev.rifflab.catalog.ArtistResponse;
import dev.rifflab.catalog.TrackRequest;
import dev.rifflab.catalog.TrackResponse;
import dev.rifflab.extraction.AudioExtractor;
import dev.rifflab.extraction.ExtractionResult;
import dev.rifflab.extraction.ExtractionResult.AudioInfo;
import dev.rifflab.extraction.ExtractionResult.BassNoteEvent;
import dev.rifflab.extraction.ExtractionResult.BeatEvent;
import dev.rifflab.extraction.ExtractionResult.ChordEvent;
import dev.rifflab.extraction.ExtractionResult.KeyEstimate;
import dev.rifflab.extraction.ExtractionResult.Provenance;
import dev.rifflab.extraction.ExtractionResult.Tempo;
import dev.rifflab.extraction.ExtractionResult.TimbreStat;
import dev.rifflab.harmony.Chord;
import dev.rifflab.harmony.ChordQuality;
import dev.rifflab.harmony.KeyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cadastro → fila → worker → chord_segment com grau → timeline. O extrator é um stub que devolve um
 * resultado conhecido (Am F G E7 em Lá menor, com o E7 tocado como power chord segundo o chroma).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "rifflab.worker.enabled=false")
@Testcontainers
class AnalysisPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class StubExtractor {
        @Bean
        @Primary
        AudioExtractor stub() {
            return new AudioExtractor() {
                @Override
                public String name() {
                    return "stub";
                }

                @Override
                public ExtractionResult analyze(Path audio, String audioSha256) {
                    return FIXTURE;
                }
            };
        }
    }

    static final ExtractionResult FIXTURE = new ExtractionResult(
            new Provenance("stub", "0", Map.of("chords", "hand-written")),
            new AudioInfo(new BigDecimal("8.000"), 44100, new BigDecimal("-14.00")),
            new KeyEstimate(9, KeyMode.MINOR, 0.9f),
            new Tempo(new BigDecimal("120.00"), "4/4"),
            List.of(new BeatEvent(bd(0.0), 1), new BeatEvent(bd(0.5), 2), new BeatEvent(bd(1.0), 3),
                    new BeatEvent(bd(1.5), 4), new BeatEvent(bd(2.0), 1)),
            List.of(
                    chord(0.0, 1.0, 9, ChordQuality.MIN, 9, 0, 4),      // Am
                    chord(1.0, 2.0, 9, ChordQuality.MIN, 9, 0, 4),      // Am de novo: funde
                    chord(2.0, 4.0, 5, ChordQuality.MAJ, 5, 9, 0),      // F
                    chord(4.0, 6.0, 7, ChordQuality.MAJ, 7, 11, 2),     // G
                    chord(6.0, 8.0, 4, ChordQuality.MAJ, 4, 11)),       // "E" sem terça no chroma → E5
            List.of(new BassNoteEvent(bd(0.0), bd(2.0), 45, 100),        // A sob Am
                    new BassNoteEvent(bd(2.0), bd(4.0), 41, 100),        // F sob F
                    new BassNoteEvent(bd(4.0), bd(6.0), 43, 100),        // G sob G
                    new BassNoteEvent(bd(6.0), bd(8.0), 45, 100)),       // A sob E5: pedal
            List.of(new TimbreStat("htdemucs", "bass", 400f, 50f, 0.02f, 1800f, 0.1f)),
            "/data/features/stub.parquet");

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(3);
    }

    private static ChordEvent chord(double start, double end, int root, ChordQuality quality, int... strongPcs) {
        float[] chroma = new float[12];
        for (int pc : strongPcs) {
            chroma[pc] = 1f;
        }
        return new ChordEvent(bd(start), bd(end), Chord.of(root, quality), chroma, null);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AnalysisWorker worker;

    @TempDir
    Path tempDir;

    @Test
    void registeredTrackIsAnalysedIntoAnAnnotatedTimeline() throws IOException {
        Path audio = tempDir.resolve("stub.wav");
        Files.writeString(audio, "stub");
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Stub", null, null), ArtistResponse.class)
                .getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Stub", 2026), AlbumResponse.class)
                .getBody().id();
        TrackResponse track = rest.postForEntity("/api/tracks",
                new TrackRequest(albumId, "Stub", 1, audio.toString()), TrackResponse.class).getBody();

        // O cadastro enfileirou; antes do worker a timeline é vazia (nada de mock na UI).
        TimelineResponse empty = rest.getForObject("/api/tracks/" + track.id() + "/timeline", TimelineResponse.class);
        assertThat(empty.runId()).isNull();
        assertThat(empty.segments()).isEmpty();

        assertThat(worker.pollOnce()).isPresent();
        assertThat(worker.pollOnce()).isEmpty();

        TrackResponse analysed = rest.getForObject("/api/tracks/" + track.id(), TrackResponse.class);
        assertThat(analysed.canonicalRunId()).isNotNull();
        assertThat(analysed.durationS()).isEqualByComparingTo("8.000");

        ResponseEntity<AnalysisRunResponse> run = rest.getForEntity("/api/analysis/runs/" + analysed.canonicalRunId(),
                AnalysisRunResponse.class);
        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(run.getBody().status()).isEqualTo(RunStatus.DONE);
        assertThat(run.getBody().extractorName()).isEqualTo("stub");
        assertThat(run.getBody().modelNames()).containsEntry("chords", "hand-written");
        assertThat(run.getBody().attempts()).isEqualTo(1);

        TimelineResponse timeline = rest.getForObject("/api/tracks/" + track.id() + "/timeline", TimelineResponse.class);
        assertThat(timeline.key().tonicPc()).isEqualTo(9);
        assertThat(timeline.key().mode()).isEqualTo(KeyMode.MINOR);
        assertThat(timeline.bpm()).isEqualByComparingTo("120.00");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::degreeLabel)
                .containsExactly("i", "♭VI", "♭VII", "V5");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::keyRelation)
                .containsExactly("DIATONIC", "DIATONIC", "DIATONIC", "AMBIGUOUS");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::quality)
                .containsExactly(ChordQuality.MIN, ChordQuality.MAJ, ChordQuality.MAJ, ChordQuality.POWER);
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::relationFromPrev)
                .containsExactly(null, "LEITTONWECHSEL", "WHOLE_TONE", "MEDIANT");
        assertThat(timeline.segments()).extracting(TimelineResponse.Segment::effectiveBassPc)
                .containsExactly(9, 5, 7, 9);
        assertThat(timeline.segments().get(0).endS()).isEqualByComparingTo("2.000");   // Am fundido
    }

    @Test
    void reanalysisCreatesASecondRunWithoutTouchingTheCanonicalOne() throws IOException {
        Path audio = tempDir.resolve("stub2.wav");
        Files.writeString(audio, "stub2");
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Stub 2", null, null), ArtistResponse.class)
                .getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Stub 2", 2026), AlbumResponse.class)
                .getBody().id();
        Long trackId = rest.postForEntity("/api/tracks",
                new TrackRequest(albumId, "Stub 2", 1, audio.toString()), TrackResponse.class).getBody().id();
        worker.pollOnce();
        Long canonical = rest.getForObject("/api/tracks/" + trackId, TrackResponse.class).canonicalRunId();

        ResponseEntity<Map> accepted = rest.postForEntity("/api/tracks/" + trackId + "/analyze", null, Map.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long secondRun = ((Number) accepted.getBody().get("runId")).longValue();
        worker.pollOnce();

        assertThat(rest.getForObject("/api/tracks/" + trackId, TrackResponse.class).canonicalRunId()).isEqualTo(canonical);
        assertThat(rest.getForObject("/api/analysis/runs/" + secondRun, AnalysisRunResponse.class).status())
                .isEqualTo(RunStatus.DONE);
        TimelineResponse second = rest.getForObject("/api/tracks/" + trackId + "/timeline?runId=" + secondRun,
                TimelineResponse.class);
        assertThat(second.runId()).isEqualTo(secondRun);
        assertThat(second.segments()).hasSize(4);
    }
}
