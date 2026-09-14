package dev.rifflab.catalog;

import dev.rifflab.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    private final TrackRepository tracks;
    private final TrackService service;

    TrackController(TrackRepository tracks, TrackService service) {
        this.tracks = tracks;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<TrackResponse> list(@RequestParam(required = false) Long albumId) {
        List<Track> result = albumId == null ? tracks.findAll() : tracks.findByAlbumIdOrderByTrackNoAsc(albumId);
        return result.stream().map(TrackResponse::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    TrackResponse get(@PathVariable Long id) {
        return TrackResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    TrackResponse create(@Valid @RequestBody TrackRequest req) {
        return TrackResponse.of(service.register(req));
    }

    /** Enfileira um novo run para a faixa; devolve o id do run para polling em /api/analysis/runs/{id}. */
    @PostMapping("/{id}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    Map<String, Long> analyze(@PathVariable Long id) {
        return Map.of("runId", service.enqueueAnalysis(id).getId());
    }

    /** O arquivo de áudio da faixa, para o player da UI. Spring MVC responde a Range (206) para seek. */
    @GetMapping("/{id}/audio")
    @Transactional(readOnly = true)
    ResponseEntity<Resource> audio(@PathVariable Long id) {
        Track track = find(id);
        Path path = Path.of(track.getAudioPath());
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("Audio of track", id);
        }
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
        if (name.endsWith(".ogg")) {
            return MediaType.parseMediaType("audio/ogg");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    public record CanonicalRunRequest(@jakarta.validation.constraints.NotNull Long runId) {
    }

    /** Troca o run que responde pela faixa (comparar extratores/modelos sem sobrescrever nada). */
    @PutMapping("/{id}/canonical-run")
    @Transactional
    TrackResponse setCanonicalRun(@PathVariable Long id, @Valid @RequestBody CanonicalRunRequest req) {
        return TrackResponse.of(service.setCanonicalRun(id, req.runId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        tracks.delete(find(id));
    }

    private Track find(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("Track", id));
    }
}
