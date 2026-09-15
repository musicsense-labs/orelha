package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.AlbumRequest;
import dev.musicsense.orelha.catalog.AlbumResponse;
import dev.musicsense.orelha.catalog.ArtistRequest;
import dev.musicsense.orelha.catalog.ArtistResponse;
import dev.musicsense.orelha.catalog.TrackRequest;
import dev.musicsense.orelha.catalog.TrackResponse;
import dev.musicsense.orelha.extraction.AudioExtractor;
import dev.musicsense.orelha.extraction.ExtractionResult;
import dev.musicsense.orelha.extraction.ExtractionResult.AudioInfo;
import dev.musicsense.orelha.extraction.ExtractionResult.BassNoteEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.BeatEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.ChordEvent;
import dev.musicsense.orelha.extraction.ExtractionResult.KeyEstimate;
import dev.musicsense.orelha.extraction.ExtractionResult.Provenance;
import dev.musicsense.orelha.extraction.ExtractionResult.Tempo;
import dev.musicsense.orelha.extraction.ExtractionResult.TimbreStat;
import dev.musicsense.orelha.harmony.Chord;
import dev.musicsense.orelha.harmony.ChordQuality;
import dev.musicsense.orelha.harmony.KeyMode;
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
        properties = {"orelha.worker.enabled=false", "orelha.harmony.power-chord-third-ratio=0.35"})
@Testcontainers
class AnalysisPipelineIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Simula o bind mount do compose: /data/... do extrator vira este diretório no host. */
    static Path dataRoot;

    @org.springframework.test.context.DynamicPropertySource
    static void dataRoot(org.springframework.test.context.DynamicPropertyRegistry registry) throws IOException {
        dataRoot = Files.createTempDirectory("orelha-data");
        Files.createDirectories(dataRoot.resolve("stems/stub"));
        Files.writeString(dataRoot.resolve("stems/stub/bass.wav"), "bass stem");
        registry.add("orelha.data.host-root", () -> dataRoot.toString());
    }

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
            "/data/features/stub.parquet",
            Map.of("bass", "/data/stems/stub/bass.wav", "drums", "/data/stems/stub/drums.wav"));

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(3);
    }

    /** strongPcs alimenta o chroma grave (o que decide POWER); o chroma da mixagem é neutro. */
    private static ChordEvent chord(double start, double end, int root, ChordQuality quality, int... strongPcs) {
        float[] chromaLow = new float[12];
        for (int pc : strongPcs) {
            chromaLow[pc] = 1f;
        }
        float[] chroma = new float[12];
        java.util.Arrays.fill(chroma, 0.5f);
        return new ChordEvent(bd(start), bd(end), Chord.of(root, quality), chroma, chromaLow, null);
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
        assertThat(timeline.key().source()).isEqualTo(KeySource.EXTRACTOR);

        // Beats do run canônico, com compasso contado a partir do primeiro downbeat.
        ResponseEntity<List<dev.musicsense.orelha.catalog.TrackController.BeatResponse>> beats = rest.exchange(
                "/api/tracks/" + track.id() + "/beats", org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(beats.getBody()).hasSize(5);
        assertThat(beats.getBody()).extracting(dev.musicsense.orelha.catalog.TrackController.BeatResponse::barNo)
                .containsExactly(1, 1, 1, 1, 2);
        assertThat(beats.getBody()).extracting(dev.musicsense.orelha.catalog.TrackController.BeatResponse::downbeat)
                .containsExactly(true, false, false, false, true);

        // Stems do run canônico: só os que existem no host são servidos.
        ResponseEntity<List<String>> stems = rest.exchange("/api/tracks/" + track.id() + "/stems",
                org.springframework.http.HttpMethod.GET, null,
                new org.springframework.core.ParameterizedTypeReference<>() {
                });
        assertThat(stems.getBody()).containsExactly("bass", "drums");
        ResponseEntity<byte[]> bass = rest.getForEntity("/api/tracks/" + track.id() + "/stems/bass", byte[].class);
        assertThat(bass.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bass.getHeaders().getContentType().toString()).isEqualTo("audio/wav");
        assertThat(new String(bass.getBody(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("bass stem");
        assertThat(rest.getForEntity("/api/tracks/" + track.id() + "/stems/drums", byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);   // declarado pelo extrator, mas o arquivo não está no host
        assertThat(rest.getForEntity("/api/tracks/" + track.id() + "/stems/vocals", byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        // Tonalidade atribuída pelo dono: re-anota sem re-extrair e passa a ser a leitura preferida.
        ResponseEntity<KeyController.KeyResponse> override = rest.exchange("/api/tracks/" + track.id() + "/key",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new KeyController.KeyRequest(0, KeyMode.MAJOR)),
                KeyController.KeyResponse.class);
        assertThat(override.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(override.getBody().source()).isEqualTo(KeySource.MANUAL);

        TimelineResponse relative = rest.getForObject("/api/tracks/" + track.id() + "/timeline", TimelineResponse.class);
        assertThat(relative.runId()).isEqualTo(timeline.runId());
        assertThat(relative.key().source()).isEqualTo(KeySource.MANUAL);
        assertThat(relative.key().tonicPc()).isZero();
        assertThat(relative.segments()).extracting(TimelineResponse.Segment::degreeLabel)
                .containsExactly("vi", "IV", "V", "III5");
        assertThat(relative.segments()).extracting(TimelineResponse.Segment::keyRelation)
                .containsExactly("DIATONIC", "DIATONIC", "DIATONIC", "AMBIGUOUS");
        assertThat(relative.segments()).extracting(TimelineResponse.Segment::effectiveBassPc)
                .containsExactly(9, 5, 7, 9);
    }

    @Test
    void keyOverrideWithoutAnalysisIsAConflict() throws IOException {
        Path audio = tempDir.resolve("stub3.wav");
        Files.writeString(audio, "stub3");
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Stub 3", null, null), ArtistResponse.class)
                .getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Stub 3", 2026), AlbumResponse.class)
                .getBody().id();
        Long trackId = rest.postForEntity("/api/tracks",
                new TrackRequest(albumId, "Stub 3", 1, audio.toString()), TrackResponse.class).getBody().id();

        ResponseEntity<org.springframework.http.ProblemDetail> response = rest.exchange("/api/tracks/" + trackId + "/key",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new KeyController.KeyRequest(7, KeyMode.MAJOR)),
                org.springframework.http.ProblemDetail.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // deixa a fila limpa para os outros testes
        worker.pollOnce();
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

        // O dono escolhe qual run responde pela faixa.
        ResponseEntity<TrackResponse> switched = rest.exchange("/api/tracks/" + trackId + "/canonical-run",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new dev.musicsense.orelha.catalog.TrackController.CanonicalRunRequest(secondRun)),
                TrackResponse.class);
        assertThat(switched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(switched.getBody().canonicalRunId()).isEqualTo(secondRun);
        assertThat(rest.getForObject("/api/tracks/" + trackId + "/timeline", TimelineResponse.class).runId())
                .isEqualTo(secondRun);

        // Apagar a faixa leva os runs e tudo que deriva deles (V5: ON DELETE CASCADE).
        ResponseEntity<Void> deleted = rest.exchange("/api/tracks/" + trackId, org.springframework.http.HttpMethod.DELETE,
                null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(rest.getForEntity("/api/tracks/" + trackId, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/analysis/runs/" + canonical, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/analysis/runs/" + secondRun, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
