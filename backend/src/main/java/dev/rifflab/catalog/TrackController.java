package dev.rifflab.catalog;

import dev.rifflab.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        tracks.delete(find(id));
    }

    private Track find(Long id) {
        return tracks.findById(id).orElseThrow(() -> new NotFoundException("Track", id));
    }
}
