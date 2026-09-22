package dev.musicsense.orelha.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.musicsense.orelha.metadata.AlbumMatcher.Candidate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente da API de busca do MusicBrainz (ws/2, JSON). Só leitura e só sob demanda: identificar um álbum é
 * escolha do dono, nunca passo do pipeline. Respeita as duas regras deles — {@code User-Agent} com contato e
 * uma requisição por segundo (a espera é feita aqui, serializada para o processo inteiro). Dados são CC0.
 */
@Component
@EnableConfigurationProperties(MusicBrainzProperties.class)
public class MusicBrainzClient {

    private static final long MIN_INTERVAL_MS = 1_100;   // 1 req/s com folga

    private final MusicBrainzProperties props;
    private final RestClient rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object gate = new Object();
    private long lastRequestAt;

    MusicBrainzClient(MusicBrainzProperties props) {
        this.props = props;
        this.rest = RestClient.builder()
                .baseUrl(props.url())
                .defaultHeader("User-Agent", props.userAgent())
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public boolean configured() {
        return props.configured();
    }

    /**
     * Candidatos a release-group para "artista + título" (o álbum conceitual, não a edição). O título vai
     * limpo do nome de box set; o artista entra como campo próprio da busca.
     */
    public List<Candidate> searchReleaseGroups(String artist, String title, int limit) {
        String clean = escape(AlbumMatcher.cleanTitle(title));
        List<Candidate> exact = search("releasegroup:\"" + clean + "\"", artist, limit);
        // A frase exata perde por um apóstrofo ("Sgt. Peppers" × "Sgt. Pepper's"): sem aspas o Lucene deles
        // casa palavra a palavra e o casador nosso decide quem é quem.
        return exact.isEmpty() ? search(clean, artist, limit) : exact;
    }

    private List<Candidate> search(String titleQuery, String artist, int limit) {
        String query = titleQuery
                + (artist == null || artist.isBlank() ? "" : " AND artist:\"" + escape(artist) + "\"");
        JsonNode root = get("/release-group?fmt=json&limit=" + limit + "&query=" + encode(query));
        List<Candidate> out = new ArrayList<>();
        for (JsonNode rg : root.path("release-groups")) {
            out.add(new Candidate(
                    rg.path("id").asText(null),
                    rg.path("title").asText(""),
                    firstArtistName(rg),
                    rg.path("primary-type").asText(null),
                    secondaryTypes(rg),
                    year(rg.path("first-release-date").asText(null)),
                    rg.path("disambiguation").asText(null)));
        }
        return out;
    }

    /** O MBID do artista do release-group escolhido, quando a busca o trouxe. */
    public String artistMbidOf(String releaseGroupMbid) {
        JsonNode root = get("/release-group/" + releaseGroupMbid + "?fmt=json&inc=artist-credits");
        JsonNode credit = root.path("artist-credit");
        return credit.isArray() && !credit.isEmpty() ? credit.get(0).path("artist").path("id").asText(null) : null;
    }

    private static List<String> secondaryTypes(JsonNode rg) {
        List<String> out = new ArrayList<>();
        for (JsonNode t : rg.path("secondary-types")) {
            out.add(t.asText());
        }
        return out;
    }

    private String firstArtistName(JsonNode rg) {
        JsonNode credit = rg.path("artist-credit");
        return credit.isArray() && !credit.isEmpty() ? credit.get(0).path("name").asText("") : "";
    }

    /** "1965-08-06" ou "1965" → 1965; o resto → null. */
    static Integer year(String date) {
        if (date == null || date.length() < 4) {
            return null;
        }
        try {
            return Integer.valueOf(date.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode get(String path) {
        if (!configured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Defina orelha.musicbrainz.contact (ORELHA_MUSICBRAINZ_CONTACT): a API exige um contato no User-Agent.");
        }
        throttle();
        try {
            // URI pronta: com String o RestClient re-codificaria os % da query (releasegroup%3A vira %253A,
            // que o Lucene deles lê como texto e devolve zero resultado).
            String body = rest.get().uri(URI.create(props.url() + path)).retrieve().body(String.class);
            return mapper.readTree(body == null ? "{}" : body);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MusicBrainz não respondeu: " + e.getMessage());
        }
    }

    /** Uma requisição por segundo, contando do fim da anterior; simples porque o uso é sob demanda. */
    private void throttle() {
        synchronized (gate) {
            long wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    private static String escape(String text) {
        return text == null ? "" : text.replaceAll("[\\\\\"]", " ").strip();
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
