package dev.musicsense.orelha.metadata;

import dev.musicsense.orelha.catalog.AlbumRequest;
import dev.musicsense.orelha.catalog.AlbumResponse;
import dev.musicsense.orelha.catalog.ArtistRequest;
import dev.musicsense.orelha.catalog.ArtistResponse;
import dev.musicsense.orelha.catalog.MetadataSource;
import dev.musicsense.orelha.metadata.AlbumMatcher.Candidate;
import dev.musicsense.orelha.metadata.MetadataController.AlbumMetadata;
import dev.musicsense.orelha.metadata.MetadataController.Candidates;
import dev.musicsense.orelha.metadata.MetadataController.IdentifyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identificar um álbum no MusicBrainz sem falar com eles: o cliente é um stub que devolve os candidatos que a
 * busca real devolveria para o nome de box set que o acervo tem. O que importa aqui é o efeito no catálogo —
 * o ano da primeira edição passa a valer como era, o da tag continua guardado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"orelha.worker.enabled=false", "orelha.musicbrainz.contact=teste@example.com"})
@Testcontainers
class MusicBrainzIdentifyIntegrationTest {

    private static final String HELP_RG = "b5dbf2b6-0000-4000-8000-000000000001";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class StubMusicBrainz {

        @Bean
        @Primary
        MusicBrainzClient stubClient(MusicBrainzProperties props) {
            return new MusicBrainzClient(props) {
                @Override
                public List<Candidate> searchReleaseGroups(String artist, String title, int limit) {
                    return List.of(
                            new Candidate("live-mbid", "Help!", "The Beatles", "Album", List.of("Live"), 1996, "live"),
                            new Candidate(HELP_RG, "Help!", "The Beatles", "Album", List.of("Soundtrack"), 1965, null),
                            new Candidate("tribute-mbid", "Help!", "Beatles Tribute Band", "Album", List.of(), 2004, null));
                }

                @Override
                public String artistMbidOf(String releaseGroupMbid) {
                    return "b10bbbfc-cf9e-42e0-be17-e2c3e1d2600d";   // The Beatles
                }
            };
        }
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void identifyingABoxSetRipFixesTheEraWithoutLosingTheEditionYear() {
        Long artistId = rest.postForEntity("/api/artists", new ArtistRequest("The Beatles", "UK", 1960),
                ArtistResponse.class).getBody().id();
        Long albumId = rest.postForEntity("/api/albums", new AlbumRequest(artistId,
                        "Studio Albums Stereo Remastered Box Set (16CD) (2009) 05 - Help (6th August 1965)", 2009),
                AlbumResponse.class).getBody().id();

        // Antes: o que veio das tags é tudo o que existe, e a era é 2009.
        AlbumResponse before = rest.getForObject("/api/albums/" + albumId, AlbumResponse.class);
        assertThat(before.effectiveYear()).isEqualTo(2009);
        assertThat(before.metadataSource()).isEqualTo(MetadataSource.TAGS);

        // Candidatos: o título vai limpo para a busca e o release-group certo ganha do ao vivo e do tributo.
        Candidates candidates = rest.getForObject("/api/albums/" + albumId + "/musicbrainz/candidates", Candidates.class);
        assertThat(candidates.searchedTitle()).isEqualTo("Help");
        assertThat(candidates.configured()).isTrue();
        assertThat(candidates.candidates().get(0).mbid()).isEqualTo(HELP_RG);
        assertThat(candidates.candidates().get(0).confident()).isTrue();
        assertThat(candidates.candidates()).filteredOn(c -> c.mbid().equals("tribute-mbid"))
                .allSatisfy(c -> assertThat(c.confident()).isFalse());

        // Aceitar a escolha: era = 1965, edição = 2009, e o artista herda o MBID.
        ResponseEntity<AlbumMetadata> identified = rest.exchange("/api/albums/" + albumId + "/musicbrainz",
                HttpMethod.PUT, new HttpEntity<>(new IdentifyRequest(HELP_RG, 1965)), AlbumMetadata.class);
        assertThat(identified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(identified.getBody().effectiveYear()).isEqualTo(1965);
        assertThat(identified.getBody().year()).isEqualTo(2009);
        assertThat(identified.getBody().source()).isEqualTo(MetadataSource.MUSICBRAINZ);

        AlbumResponse after = rest.getForObject("/api/albums/" + albumId, AlbumResponse.class);
        assertThat(after.effectiveYear()).isEqualTo(1965);
        assertThat(after.firstReleased()).isEqualTo(1965);
        assertThat(after.year()).isEqualTo(2009);
        assertThat(after.mbid()).isEqualTo(HELP_RG);
        assertThat(rest.getForObject("/api/artists/" + artistId, ArtistResponse.class)).isNotNull();

        // Desfazer devolve a era da tag.
        ResponseEntity<AlbumMetadata> forgotten = rest.exchange("/api/albums/" + albumId + "/musicbrainz",
                HttpMethod.DELETE, null, AlbumMetadata.class);
        assertThat(forgotten.getBody().effectiveYear()).isEqualTo(2009);
        assertThat(forgotten.getBody().source()).isEqualTo(MetadataSource.TAGS);
        assertThat(rest.getForObject("/api/albums/" + albumId, AlbumResponse.class).mbid()).isNull();
    }
}
