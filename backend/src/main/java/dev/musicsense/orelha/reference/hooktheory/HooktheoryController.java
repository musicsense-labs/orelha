package dev.musicsense.orelha.reference.hooktheory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Consulta sob demanda ao Trends do Hooktheory. {@code cp} são os ids de acorde do Hooktheory separados por
 * vírgula ("4,1" = IV I); a UI monta a sequência a partir dos graus diatônicos de uma parte.
 */
@RestController
@RequestMapping("/api/reference/hooktheory")
public class HooktheoryController {

    private final HooktheoryClient client;

    HooktheoryController(HooktheoryClient client) {
        this.client = client;
    }

    @GetMapping("/status")
    Map<String, Boolean> status() {
        return Map.of("configured", client.configured());
    }

    @GetMapping("/trends")
    List<HooktheoryClient.Node> trends(@RequestParam(required = false) String cp) {
        return client.nodes(cp);
    }

    @GetMapping("/songs")
    List<HooktheoryClient.Song> songs(@RequestParam String cp, @RequestParam(defaultValue = "1") int page) {
        return client.songs(cp, page);
    }
}
