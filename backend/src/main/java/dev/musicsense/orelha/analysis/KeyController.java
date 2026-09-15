package dev.musicsense.orelha.analysis;

import dev.musicsense.orelha.catalog.Track;
import dev.musicsense.orelha.catalog.TrackRepository;
import dev.musicsense.orelha.common.NotFoundException;
import dev.musicsense.orelha.harmony.KeyMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tonalidade atribuída pelo dono (inclusive modos), aplicada ao run canônico sem re-extrair. */
@RestController
@RequestMapping("/api/tracks/{trackId}/key")
public class KeyController {

    public record KeyRequest(@Min(0) @Max(11) int tonicPc, @NotNull KeyMode mode) {
    }

    public record KeyResponse(Long runId, Long keySegmentId, int tonicPc, KeyMode mode, KeySource source) {
    }

    private final TrackRepository tracks;
    private final AnalysisPipeline pipeline;

    KeyController(TrackRepository tracks, AnalysisPipeline pipeline) {
        this.tracks = tracks;
        this.pipeline = pipeline;
    }

    @PutMapping
    KeyResponse override(@PathVariable Long trackId, @Valid @RequestBody KeyRequest req) {
        Track track = tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
        AnalysisRun run = track.getCanonicalRun();
        if (run == null) {
            throw new IllegalStateException("Track " + trackId + " has no completed analysis to re-annotate");
        }
        KeySegment key = pipeline.reannotate(run.getId(), req.tonicPc(), req.mode());
        return new KeyResponse(run.getId(), key.getId(), key.getTonicPc(), key.getMode(), key.getSource());
    }
}
