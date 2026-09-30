package dev.musicsense.orelha.catalog;

import dev.musicsense.orelha.analysis.AnalysisRun;
import dev.musicsense.orelha.common.NotFoundException;
import dev.musicsense.orelha.extraction.DataPaths;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * O que o player toca: a mixagem (o arquivo da biblioteca) e os stems do run canônico. Spring MVC responde
 * a {@code Range} com 206, que é o que o {@code <audio>} usa para o seek.
 */
@RestController
@RequestMapping("/api/tracks/{id}")
public class MediaController {

    private final TrackRepository tracks;
    private final AudioLibrary library;
    private final DataPaths dataPaths;

    MediaController(TrackRepository tracks, AudioLibrary library, DataPaths dataPaths) {
        this.tracks = tracks;
        this.library = library;
        this.dataPaths = dataPaths;
    }

    @GetMapping("/audio")
    @Transactional(readOnly = true)
    ResponseEntity<Resource> audio(@PathVariable Long id) {
        Path path = library.resolve(find(id));
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Audio of track", id);
        }
        return audioResponse(path);
    }

    /** Nomes dos stems do run canônico; vazio sem análise. */
    @GetMapping("/stems")
    @Transactional(readOnly = true)
    List<String> stems(@PathVariable Long id) {
        return stemsOf(find(id)).keySet().stream().sorted().toList();
    }

    @GetMapping("/stems/{name}")
    @Transactional(readOnly = true)
    ResponseEntity<Resource> stem(@PathVariable Long id, @PathVariable String name) {
        String containerPath = stemsOf(find(id)).get(name);
        Path path = containerPath == null ? null : dataPaths.toHost(containerPath);
        if (path == null || !Files.isRegularFile(path)) {
            throw new NotFoundException("Stem " + name + " of track", id);
        }
        return audioResponse(path);
    }

    private Track find(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("Track", id));
    }

    private static Map<String, String> stemsOf(Track track) {
        AnalysisRun run = track.getCanonicalRun();
        return run == null || run.getStems() == null ? Map.of() : run.getStems();
    }

    private static ResponseEntity<Resource> audioResponse(Path path) {
        return ResponseEntity.ok()
                .contentType(audioType(path))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(new FileSystemResource(path));
    }

    private static MediaType audioType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp3")) {
            return MediaType.parseMediaType("audio/mpeg");
        }
        if (name.endsWith(".wav")) {
            return MediaType.parseMediaType("audio/wav");
        }
        if (name.endsWith(".flac")) {
            return MediaType.parseMediaType("audio/flac");
        }
        if (name.endsWith(".ogg") || name.endsWith(".opus")) {
            return MediaType.parseMediaType("audio/ogg");
        }
        if (name.endsWith(".m4a") || name.endsWith(".aac")) {
            return MediaType.parseMediaType("audio/mp4");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
