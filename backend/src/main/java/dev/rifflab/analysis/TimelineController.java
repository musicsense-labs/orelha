package dev.rifflab.analysis;

import dev.rifflab.catalog.Track;
import dev.rifflab.catalog.TrackRepository;
import dev.rifflab.common.NotFoundException;
import dev.rifflab.harmony.HarmonicNormalizer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tracks/{trackId}/timeline")
public class TimelineController {

    private final TrackRepository tracks;
    private final AnalysisRunRepository runs;
    private final KeySegmentRepository keys;
    private final HarmonicAnnotationRepository annotations;
    private final TrackAnalysisRepository analyses;

    TimelineController(TrackRepository tracks, AnalysisRunRepository runs, KeySegmentRepository keys,
                       HarmonicAnnotationRepository annotations, TrackAnalysisRepository analyses) {
        this.tracks = tracks;
        this.runs = runs;
        this.keys = keys;
        this.annotations = annotations;
        this.analyses = analyses;
    }

    /**
     * Timeline do run canônico da faixa, ou de um run específico via ?runId=, lida com a tonalidade
     * preferida (MANUAL > DERIVED > EXTRACTOR). Vazio se nada foi analisado.
     */
    @GetMapping
    @Transactional(readOnly = true)
    TimelineResponse get(@PathVariable Long trackId, @RequestParam(required = false) Long runId) {
        Track track = tracks.findById(trackId).orElseThrow(() -> new NotFoundException("Track", trackId));
        AnalysisRun run = runId != null
                ? runs.findById(runId).orElseThrow(() -> new NotFoundException("AnalysisRun", runId))
                : track.getCanonicalRun();
        if (run == null || !run.getTrack().getId().equals(trackId)) {
            return new TimelineResponse(trackId, null, HarmonicNormalizer.VERSION, null, null, null, List.of());
        }
        KeySegment key = keys.findPreferred(run.getId()).orElse(null);
        List<HarmonicAnnotation> timeline = key == null ? List.of()
                : annotations.findTimeline(run.getId(), HarmonicNormalizer.VERSION, key.getId());
        TrackAnalysis analysis = analyses.findByRunId(run.getId()).orElse(null);
        return new TimelineResponse(trackId, run.getId(), HarmonicNormalizer.VERSION,
                key == null ? null : new TimelineResponse.KeyInfo(key.getTonicPc(), key.getMode(), key.getConfidence(), key.getSource()),
                analysis == null ? null : analysis.getBpm(),
                analysis == null ? null : analysis.getTimeSignature(),
                timeline.stream().map(TimelineResponse.Segment::of).toList());
    }
}
