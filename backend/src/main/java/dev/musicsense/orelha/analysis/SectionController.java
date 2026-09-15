package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Partes da música (resumo harmônico por parte). GET lê a fonte preferida (MANUAL > EXTRACTOR > DERIVED)
 * do run canônico ou de ?runId=; PUT grava a edição do dono (lista vazia volta à derivação); POST /derive
 * re-deriva por repetição (as MANUAL ficam).
 */
@RestController
@RequestMapping("/api/tracks/{trackId}/sections")
public class SectionController {

    public record SectionRequest(@NotNull BigDecimal startS, @NotNull BigDecimal endS, @NotBlank String label,
                                 BigDecimal cycleEndS, Integer repeats) {
    }

    private final TrackRepository tracks;
    private final AnalysisRunRepository runs;
    private final SectionService service;

    SectionController(TrackRepository tracks, AnalysisRunRepository runs, SectionService service) {
        this.tracks = tracks;
        this.runs = runs;
        this.service = service;
    }

    @GetMapping
    @Transactional(readOnly = true)
    SectionsResponse get(@PathVariable Long trackId, @RequestParam(required = false) Long runId) {
        Track track = find(trackId);
        AnalysisRun run = runId != null
                ? runs.findById(runId).orElseThrow(() -> new NotFoundException("AnalysisRun", runId))
                : track.getCanonicalRun();
        if (run == null || !run.getTrack().getId().equals(trackId)) {
            return new SectionsResponse(trackId, null, null, List.of());
        }
        List<SectionsResponse.Part> parts = service.read(run);
        return new SectionsResponse(trackId, run.getId(), parts.isEmpty() ? null : sourceOf(run), parts);
    }

    @PutMapping
    @Transactional
    SectionsResponse replace(@PathVariable Long trackId, @RequestBody List<@Valid SectionRequest> body) {
        AnalysisRun run = canonicalRun(trackId);
        service.replaceManual(run, body.stream()
                .map(r -> new SectionService.SectionEdit(r.startS(), r.endS(), r.label(), r.cycleEndS(), r.repeats()))
                .toList());
        return get(trackId, null);
    }

    @PostMapping("/derive")
    @Transactional
    @ResponseStatus(HttpStatus.OK)
    SectionsResponse derive(@PathVariable Long trackId) {
        service.derive(canonicalRun(trackId));
        return get(trackId, null);
    }

    private AnalysisRun canonicalRun(Long trackId) {
        Track track = find(trackId);
        if (track.getCanonicalRun() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Track " + trackId + " has no analysed run yet");
        }
        return track.getCanonicalRun();
    }

    private SectionSource sourceOf(AnalysisRun run) {
        return service.preferredSource(run);
    }

    private Track find(Long trackId) {
        return tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
    }
}
