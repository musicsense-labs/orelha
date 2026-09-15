package dev.musicsense.orelha.analysis;

import java.time.Instant;
import java.util.Map;

/** Status e proveniência de um run. */
public record AnalysisRunResponse(Long id, Long trackId, RunStatus status, String extractorName,
                                  String extractorVersion, Map<String, String> modelNames, int attempts,
                                  String error, String featuresPath, Instant requestedAt, Instant startedAt,
                                  Instant finishedAt) {

    static AnalysisRunResponse of(AnalysisRun r) {
        return new AnalysisRunResponse(r.getId(), r.getTrack().getId(), r.getStatus(), r.getExtractorName(),
                r.getExtractorVersion(), r.getModelNames(), r.getAttempts(), r.getError(), r.getFeaturesPath(),
                r.getRequestedAt(), r.getStartedAt(), r.getFinishedAt());
    }
}
