package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Letra por ASR do run canônico (ou de ?runId=), com compasso e ataque de nota por palavra. PUT grava a
 * correção do dono (lista inteira, vira MANUAL; lista vazia volta à transcrição). Vazia antes do extrator 0.6.0.
 */
@RestController
@RequestMapping("/api/tracks/{trackId}/lyrics")
public class LyricsController {

    public record WordRequest(@NotNull BigDecimal startS, @NotNull BigDecimal endS, String text) {
    }

    public record SegmentRequest(@NotNull BigDecimal startS, @NotNull BigDecimal endS, String text,
                                 List<@Valid WordRequest> words) {
    }

    private final TrackRepository tracks;
    private final AnalysisRunRepository runs;
    private final LyricsService service;

    LyricsController(TrackRepository tracks, AnalysisRunRepository runs, LyricsService service) {
        this.tracks = tracks;
        this.runs = runs;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    LyricsResponse get(@PathVariable Long trackId, @RequestParam(required = false) Long runId) {
        Track track = find(trackId);
        AnalysisRun run = runId != null
                ? runs.findById(runId).orElseThrow(() -> new NotFoundException("AnalysisRun", runId))
                : track.getCanonicalRun();
        if (run == null || !run.getTrack().getId().equals(trackId)) {
            return new LyricsResponse(null, null, null, null, List.of());
        }
        return service.response(run);
    }

    @PutMapping
    @Transactional
    LyricsResponse replace(@PathVariable Long trackId, @RequestBody List<@Valid SegmentRequest> body) {
        Track track = find(trackId);
        if (track.getCanonicalRun() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Track " + trackId + " has no analysed run yet");
        }
        service.replaceManual(track.getCanonicalRun(), body.stream()
                .map(s -> new LyricsService.SegmentEdit(s.startS(), s.endS(), s.text(),
                        s.words() == null ? List.of() : s.words().stream()
                                .map(w -> new LyricsService.WordEdit(w.startS(), w.endS(), w.text()))
                                .toList()))
                .toList());
        return get(trackId, null);
    }

    private Track find(Long trackId) {
        return tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
    }
}
