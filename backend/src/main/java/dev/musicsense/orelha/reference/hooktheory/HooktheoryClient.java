package dev.musicsense.orelha.reference.hooktheory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cliente da API Trends do Hooktheory (documentada em hooktheory.com/api/trends/docs): próximo acorde
 * mais provável dada uma sequência e canções que contêm a sequência. A API limita a 10 pedidos por
 * 10 s, então toda resposta fica em cache local por um dia. Só sob demanda, nunca no pipeline.
 * A sequência ({@code cp}) usa os ids do próprio Hooktheory ("1", "4", "5", "6"…), que o chamador monta.
 */
@Component
@EnableConfigurationProperties(HooktheoryProperties.class)
public class HooktheoryClient {

    public record Node(String chordId, String chordHtml, double probability, String childPath) {
    }

    public record Song(String artist, String song, String section, String url) {
    }

    private record Cached<T>(T value, Instant at) {
    }

    private static final Duration TTL = Duration.ofDays(1);

    private final HooktheoryProperties props;
    private final RestClient rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Cached<?>> cache = new ConcurrentHashMap<>();

    HooktheoryClient(HooktheoryProperties props) {
        this.props = props;
        this.rest = RestClient.builder().baseUrl(props.url()).build();
    }

    public boolean configured() {
        return props.configured();
    }

    /** Próximos acordes prováveis depois da sequência {@code cp} (vazio = começo de progressão). */
    public List<Node> nodes(String cp) {
        return cached("nodes:" + cp, () -> {
            String body = get("trends/nodes" + (cp == null || cp.isBlank() ? "" : "?cp=" + cp));
            List<Map<String, Object>> raw = read(body);
            return raw.stream().map(m -> new Node(str(m.get("chord_ID")), str(m.get("chord_HTML")),
                    m.get("probability") instanceof Number n ? n.doubleValue() : 0, str(m.get("child_path")))).toList();
        });
    }

    /** Canções do banco que contêm a sequência {@code cp}. */
    public List<Song> songs(String cp, int page) {
        return cached("songs:" + cp + ":" + page, () -> {
            List<Map<String, Object>> raw = read(get("trends/songs?cp=" + cp + "&page=" + page));
            return raw.stream().map(m -> new Song(str(m.get("artist")), str(m.get("song")), str(m.get("section")),
                    str(m.get("url")))).toList();
        });
    }

    private String get(String path) {
        if (!configured()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hooktheory não configurado: defina ORELHA_HOOKTHEORY_ACTIVKEY com o token da sua conta");
        }
        return rest.get().uri(path)
                .header("Authorization", "Bearer " + props.activkey())
                .header("Accept", "application/json")
                .retrieve()
                .body(String.class);
    }

    private List<Map<String, Object>> read(String body) {
        try {
            return mapper.readValue(body == null ? "[]" : body, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Resposta inesperada do Hooktheory: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, java.util.function.Supplier<T> loader) {
        Cached<?> hit = cache.get(key);
        if (hit != null && hit.at().plus(TTL).isAfter(Instant.now())) {
            return (T) hit.value();
        }
        T value = loader.get();
        cache.put(key, new Cached<>(value, Instant.now()));
        return value;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
