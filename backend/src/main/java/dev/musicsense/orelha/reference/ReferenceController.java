package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.common.NotFoundException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Análise humana de referência de uma faixa (TheoryTab transcrito pelo dono): GET lê, PUT grava o texto
 * colado (tonalidade + seções em numerais), DELETE apaga, GET /compare mede contra a nossa análise.
 */
@RestController
@RequestMapping("/api/tracks/{trackId}/reference")
public class ReferenceController {

    public record ReferenceRequest(@NotBlank String text, String url) {
    }

    private final TrackRepository tracks;
    private final ReferenceService service;

    ReferenceController(TrackRepository tracks, ReferenceService service) {
        this.tracks = tracks;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    ReferenceResponse get(@PathVariable Long trackId) {
        find(trackId);
        return service.find(trackId).map(ReferenceController::response)
                .orElse(new ReferenceResponse(trackId, null, null, null, null, null, List.of(), null));
    }

    @PutMapping
    @Transactional
    ReferenceResponse put(@PathVariable Long trackId, @RequestBody ReferenceRequest body) {
        return response(service.save(find(trackId), body.text(), body.url()));
    }

    @DeleteMapping
    @Transactional
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long trackId) {
        find(trackId);
        service.delete(trackId);
    }

    /** null (204) quando não há referência ou run canônico. */
    @GetMapping("/compare")
    @Transactional(readOnly = true)
    ComparisonResponse compare(@PathVariable Long trackId) {
        return service.compare(find(trackId));
    }

    private static ReferenceResponse response(ReferenceAnalysis r) {
        return new ReferenceResponse(r.getTrack().getId(), r.getSource(), r.getUrl(), r.getTonicPc(), r.getMode(),
                r.getRawText(), r.getSections(), r.getUpdatedAt());
    }

    private Track find(Long trackId) {
        return tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
    }
}
