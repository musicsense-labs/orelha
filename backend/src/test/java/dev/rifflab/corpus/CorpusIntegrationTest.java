package dev.rifflab.corpus;

import dev.rifflab.analysis.AnalysisWorker;
import dev.rifflab.catalog.AlbumRequest;
import dev.rifflab.catalog.AlbumResponse;
import dev.rifflab.catalog.ArtistRequest;
import dev.rifflab.catalog.ArtistResponse;
import dev.rifflab.catalog.TrackRequest;
import dev.rifflab.catalog.TrackResponse;
import dev.rifflab.corpus.CorpusMetrics.PedalPassage;
import dev.rifflab.corpus.CorpusService.Comparison;
import dev.rifflab.extraction.AudioExtractor;
import dev.rifflab.extraction.ExtractionResult;
import dev.rifflab.extraction.ExtractionResult.AudioInfo;
import dev.rifflab.extraction.ExtractionResult.BassNoteEvent;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
import static org.assertj.core.api.Assertions.within;

/**
 * Dois artistas com resultados de extração conhecidos → perfis, comparação e passagens de pedal.
 * "Sabbath" toca I ♭VI V com baixo parado sob o mediante; "Beatles" toca I IV V I, tudo diatônico.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "rifflab.worker.enabled=false")
@Testcontainers
class CorpusIntegrationTest {

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
                    return audio.getFileName().toString().startsWith("sabbath") ? SABBATH : BEATLES;
                }
            };
        }
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(3);
    }

    private static ChordEvent chord(double start, double end, int root, ChordQuality quality) {
        return new ChordEvent(bd(start), bd(end), Chord.of(root, quality), new float[12], new float[12], null);
    }

    private static BassNoteEvent bass(double start, double end, int midi) {
        return new BassNoteEvent(bd(start), bd(end), midi, 100);
    }

    private static ExtractionResult result(int tonic, KeyMode mode, List<ChordEvent> chords, List<BassNoteEvent> bass,
                                           float centroid) {
        return new ExtractionResult(new Provenance("stub", "0", Map.of()),
                new AudioInfo(new BigDecimal("8.000"), 44100, new BigDecimal("-14.00")),
                new KeyEstimate(tonic, mode, 0.9f), new Tempo(new BigDecimal("120.00"), "4/4"),
                List.of(), chords, bass, List.of(new TimbreStat("htdemucs", "other", centroid, 10f, 0.1f, 3000f, 0.2f)),
                "/data/features/stub.parquet");
    }

    // C maior: C (0–2) → A♭ (2–4, ♭VI, mediante cromático) → G (4–6) → C (6–8); baixo em C durante C→A♭.
    static final ExtractionResult SABBATH = result(0, KeyMode.MAJOR,
            List.of(chord(0, 2, 0, ChordQuality.MAJ), chord(2, 4, 8, ChordQuality.MAJ),
                    chord(4, 6, 7, ChordQuality.MAJ), chord(6, 8, 0, ChordQuality.MAJ)),
            List.of(bass(0, 4, 36), bass(4, 6, 43), bass(6, 8, 36)), 2500f);

    // C maior: C F G C, baixo na fundamental.
    static final ExtractionResult BEATLES = result(0, KeyMode.MAJOR,
            List.of(chord(0, 2, 0, ChordQuality.MAJ), chord(2, 4, 5, ChordQuality.MAJ),
                    chord(4, 6, 7, ChordQuality.MAJ), chord(6, 8, 0, ChordQuality.MAJ)),
            List.of(bass(0, 2, 36), bass(2, 4, 41), bass(4, 6, 43), bass(6, 8, 36)), 1200f);

    @Autowired
    TestRestTemplate rest;

    @Autowired
    AnalysisWorker worker;

    @TempDir
    Path tempDir;

    private long registerArtist(String name, String album, int year, String... audioNames) throws IOException {
        long artistId = rest.postForEntity("/api/artists", new ArtistRequest(name, null, null), ArtistResponse.class)
                .getBody().id();
        long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, album, year), AlbumResponse.class)
                .getBody().id();
        int no = 1;
        for (String audioName : audioNames) {
            Path audio = tempDir.resolve(audioName);
            Files.writeString(audio, audioName);
            rest.postForEntity("/api/tracks", new TrackRequest(albumId, audioName, no++, audio.toString()), TrackResponse.class);
            worker.pollOnce();
        }
        return artistId;
    }

    @Test
    void profilesCompareAndPedalPassages() throws IOException {
        long sabbath = registerArtist("Sabbath", "Paranoid", 1970, "sabbath-1.wav", "sabbath-2.wav");
        long beatles = registerArtist("Beatles", "Please", 1963, "beatles-1.wav");

        HarmonicProfile ps = rest.getForObject("/api/corpus/artists/" + sabbath + "/profile", HarmonicProfile.class);
        assertThat(ps.tracks()).isEqualTo(2);
        assertThat(ps.segments()).isEqualTo(8);
        assertThat(ps.durationS()).isEqualTo(16.0);
        assertThat(ps.nonDiatonic().bySegment()).isEqualTo(0.25);          // ♭VI em 1 de 4 por faixa
        assertThat(ps.keyRelations().get("BORROWED").byDuration()).isEqualTo(0.25);
        assertThat(ps.degrees().bySegment()[0]).isEqualTo(0.5);            // I em 2 de 4
        assertThat(ps.degrees().entropyBySegmentBits()).isCloseTo(1.5, within(1e-9)); // {0.5, 0.25, 0.25}
        assertThat(ps.transitions().total()).isEqualTo(6);                 // 3 transições × 2 faixas
        assertThat(ps.transitions().rowNormalized()[0][8]).isEqualTo(1.0); // de I sempre para ♭VI
        assertThat(ps.relations().get("CHROMATIC_MEDIANT").bySegment()).isCloseTo(1.0 / 3, within(1e-9));
        assertThat(ps.timbreByAlbum()).hasSize(1);
        assertThat(ps.timbreByAlbum().get(0).albumYear()).isEqualTo(1970);
        assertThat(ps.timbreByAlbum().get(0).tracks()).isEqualTo(2);
        assertThat(ps.timbreByAlbum().get(0).centroidMean()).isEqualTo(2500.0);

        HarmonicProfile pb = rest.getForObject("/api/corpus/artists/" + beatles + "/profile", HarmonicProfile.class);
        assertThat(pb.nonDiatonic().bySegment()).isZero();
        assertThat(pb.relations()).doesNotContainKey("CHROMATIC_MEDIANT");

        Comparison cmp = rest.getForObject("/api/corpus/compare?a=" + sabbath + "&b=" + beatles, Comparison.class);
        assertThat(cmp.distances().transitionJsBits()).isBetween(0.3, 1.0);   // I→♭VI vs I→IV não se sobrepõem
        assertThat(cmp.distances().degreeL1()).isCloseTo(0.5, within(1e-9));  // ♭VI (0.25) troca por IV (0.25)
        assertThat(cmp.distances().keyRelationL1()).isCloseTo(0.5, within(1e-9));
        Comparison reverse = rest.getForObject("/api/corpus/compare?a=" + beatles + "&b=" + sabbath, Comparison.class);
        assertThat(reverse.distances().transitionJsBits()).isEqualTo(cmp.distances().transitionJsBits());

        ResponseEntity<List<PedalPassage>> pedals = rest.exchange(
                "/api/corpus/artists/" + sabbath + "/pedal-passages", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {
                });
        assertThat(pedals.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pedals.getBody()).hasSize(2);                           // uma por faixa
        assertThat(pedals.getBody().get(0).fromLabel()).isEqualTo("I");
        assertThat(pedals.getBody().get(0).toLabel()).isEqualTo("♭VI");
        assertThat(pedals.getBody().get(0).bassPc()).isZero();
        assertThat(pedals.getBody().get(0).endS()).isEqualTo(4.0);

        HarmonicProfile album = rest.getForObject("/api/corpus/albums/" + ps.timbreByAlbum().get(0).albumId() + "/profile",
                HarmonicProfile.class);
        assertThat(album.scope()).isEqualTo("album");
        assertThat(album.segments()).isEqualTo(8);
    }

    @Test
    void unknownArtistIsNotFoundAndEmptyArtistIsEmpty() {
        assertThat(rest.getForEntity("/api/corpus/artists/999999/profile", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        long empty = rest.postForEntity("/api/artists", new ArtistRequest("Nobody", null, null), ArtistResponse.class)
                .getBody().id();
        HarmonicProfile p = rest.getForObject("/api/corpus/artists/" + empty + "/profile", HarmonicProfile.class);
        assertThat(p.tracks()).isZero();
        assertThat(p.segments()).isZero();
        assertThat(p.transitions().total()).isZero();
        assertThat(p.degrees().entropyBySegmentBits()).isZero();
    }
}
