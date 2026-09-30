package dev.musicsense.orelha.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe Postgres real, roda o Flyway, valida o mapeamento JPA (ddl-auto=validate)
 * e percorre o CRUD artist → album → track pela API.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "orelha.worker.enabled=false")
@Testcontainers
class CatalogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.test.context.DynamicPropertySource
    static void libraryDir(org.springframework.test.context.DynamicPropertyRegistry registry) throws IOException {
        Path dir = Files.createTempDirectory("orelha-library");
        registry.add("orelha.library.dir", dir::toString);
    }

    @Autowired
    TestRestTemplate rest;

    @TempDir
    Path tempDir;

    @Test
    void registersArtistAlbumAndTrackWithAudioIdentity() throws IOException {
        Path audio = tempDir.resolve("war-pigs.wav");
        Files.writeString(audio, "not really audio", StandardCharsets.UTF_8);
        String expectedSha = "32f576369ee502b18d21578f922744cac65e90df9679cea34ad4bb0b56035e73"; // sha256("not really audio")

        ResponseEntity<ArtistResponse> artist = rest.postForEntity("/api/artists",
                new ArtistRequest("Black Sabbath", "UK", 1968), ArtistResponse.class);
        assertThat(artist.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(artist.getBody().id()).isNotNull();

        ResponseEntity<AlbumResponse> album = rest.postForEntity("/api/albums",
                new AlbumRequest(artist.getBody().id(), "Paranoid", 1970), AlbumResponse.class);
        assertThat(album.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(album.getBody().artistId()).isEqualTo(artist.getBody().id());

        ResponseEntity<TrackResponse> track = Uploads.upload(rest, album.getBody().id(), "War Pigs", 1, audio);
        assertThat(track.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        TrackResponse created = track.getBody();
        assertThat(created.audioSha256()).isEqualTo(expectedSha);
        assertThat(created.durationS()).isNull();
        assertThat(created.canonicalRunId()).isNull();

        ResponseEntity<TrackResponse> fetched = rest.getForEntity("/api/tracks/" + created.id(), TrackResponse.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody()).isEqualTo(created);

        ResponseEntity<List<TrackResponse>> byAlbum = rest.exchange("/api/tracks?albumId=" + album.getBody().id(),
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {
                });
        assertThat(byAlbum.getBody()).containsExactly(created);

        // O player da UI lê o áudio pela API, com Range para seek.
        ResponseEntity<byte[]> audioBytes = rest.getForEntity("/api/tracks/" + created.id() + "/audio", byte[].class);
        assertThat(audioBytes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(audioBytes.getHeaders().getContentType().toString()).isEqualTo("audio/wav");
        assertThat(new String(audioBytes.getBody(), StandardCharsets.UTF_8)).isEqualTo("not really audio");

        org.springframework.http.HttpHeaders range = new org.springframework.http.HttpHeaders();
        range.set("Range", "bytes=4-9");
        ResponseEntity<byte[]> partial = rest.exchange("/api/tracks/" + created.id() + "/audio", HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(range), byte[].class);
        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(new String(partial.getBody(), StandardCharsets.UTF_8)).isEqualTo("really");
    }

    @Test
    void uploadsAudioIntoTheLibraryAndQueuesAnalysis() {
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Iron Maiden", "UK", 1975), ArtistResponse.class)
                .getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Powerslave", 1984), AlbumResponse.class)
                .getBody().id();

        org.springframework.util.MultiValueMap<String, Object> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("file", new org.springframework.core.io.ByteArrayResource("fake mp3 bytes".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "Aces High: live?.mp3";
            }
        });
        form.add("albumId", albumId.toString());
        form.add("trackNo", "1");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> raw = rest.postForEntity("/api/tracks/upload",
                new org.springframework.http.HttpEntity<>(form, headers), String.class);
        assertThat(raw.getStatusCode()).as(raw.getBody()).isEqualTo(HttpStatus.CREATED);
        TrackResponse track;
        try {
            track = new com.fasterxml.jackson.databind.ObjectMapper().readValue(raw.getBody(), TrackResponse.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AssertionError(raw.getBody(), e);
        }
        assertThat(track.title()).isEqualTo("Aces High: live?");           // título = nome do arquivo sem extensão
        assertThat(track.audioPath()).endsWith(albumId + java.io.File.separator + "Aces High- live-.mp3");
        assertThat(Path.of(track.audioPath())).exists();
        assertThat(track.audioSha256()).isEqualTo(TrackService.sha256(Path.of(track.audioPath())));

        // Mesmo nome de novo (sem trackNo, que é único por álbum): o arquivo não é sobrescrito.
        form.remove("trackNo");
        ResponseEntity<TrackResponse> again = rest.postForEntity("/api/tracks/upload",
                new org.springframework.http.HttpEntity<>(form, headers), TrackResponse.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(again.getBody().audioPath()).endsWith("Aces High- live--2.mp3");

        form.set("file", new org.springframework.core.io.ByteArrayResource("nope".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "notes.txt";
            }
        });
        ResponseEntity<ProblemDetail> rejected = rest.postForEntity("/api/tracks/upload",
                new org.springframework.http.HttpEntity<>(form, headers), ProblemDetail.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aTrackCannotBeRegisteredByAServerPath() {
        // Era o cadastro por caminho: qualquer usuário do Access registrava um arquivo da máquina (a chave do
        // túnel, por exemplo) e o baixava por /audio. Removido em 2026-09-30; o upload copia o arquivo e o
        // importar pasta do servidor só roda localmente.
        ResponseEntity<ArtistResponse> artist = rest.postForEntity("/api/artists",
                new ArtistRequest("Deep Purple", "UK", 1968), ArtistResponse.class);
        ResponseEntity<AlbumResponse> album = rest.postForEntity("/api/albums",
                new AlbumRequest(artist.getBody().id(), "Machine Head", 1972), AlbumResponse.class);

        ResponseEntity<String> response = rest.postForEntity("/api/tracks",
                java.util.Map.of("albumId", album.getBody().id(), "title", "Highway Star",
                        "audioPath", tempDir.resolve("secret.txt").toString()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void onlyTheAdminRenamesOrDeletesArtistsAndAlbums() {
        long artistId = rest.postForEntity("/api/artists", new ArtistRequest("Rainbow", "UK", 1975), ArtistResponse.class)
                .getBody().id();
        long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId, "Rising", 1976), AlbumResponse.class)
                .getBody().id();
        org.springframework.http.HttpHeaders friend = new org.springframework.http.HttpHeaders();
        friend.set(dev.musicsense.orelha.common.RemoteAccess.EMAIL_HEADER, "amigo@example.com");
        friend.set(dev.musicsense.orelha.common.RemoteAccess.ORIGIN_IP_HEADER, "203.0.113.9");

        assertThat(rest.exchange("/api/artists/" + artistId, HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new ArtistRequest("Deep Purple", null, null), friend), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/albums/" + albumId, HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(new AlbumRequest(artistId, "Machine Head", 1972), friend), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/albums/" + albumId, HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(friend), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/artists/" + artistId, HttpMethod.DELETE,
                new org.springframework.http.HttpEntity<>(friend), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.getForObject("/api/artists/" + artistId, ArtistResponse.class).name()).isEqualTo("Rainbow");

        // O dono (acesso local) continua podendo.
        assertThat(rest.exchange("/api/albums/" + albumId, HttpMethod.DELETE, null, String.class).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void unknownArtistIsNotFound() {
        ResponseEntity<ProblemDetail> response = rest.getForEntity("/api/artists/999999", ProblemDetail.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void duplicateArtistNameIsConflict() {
        rest.postForEntity("/api/artists", new ArtistRequest("Judas Priest", "UK", 1969), ArtistResponse.class);
        ResponseEntity<ProblemDetail> dup = rest.postForEntity("/api/artists",
                new ArtistRequest("Judas Priest", "UK", 1969), ProblemDetail.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
