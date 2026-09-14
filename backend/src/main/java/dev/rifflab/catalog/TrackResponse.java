package dev.rifflab.catalog;

import dev.rifflab.analysis.AnalysisRun;
import dev.rifflab.analysis.RunStatus;

import java.math.BigDecimal;

/** Faixa + o run mais recente (para a UI mostrar "analisando…", DONE ou FAILED sem outra chamada). */
public record TrackResponse(Long id, Long albumId, String title, Integer trackNo, BigDecimal durationS,
                            String audioPath, String audioSha256, Integer sampleRate, Long canonicalRunId,
                            Long latestRunId, RunStatus latestRunStatus, String latestRunError) {

    static TrackResponse of(Track t, AnalysisRun latestRun) {
        return new TrackResponse(t.getId(), t.getAlbum().getId(), t.getTitle(), t.getTrackNo(), t.getDurationS(),
                t.getAudioPath(), t.getAudioSha256(), t.getSampleRate(),
                t.getCanonicalRun() == null ? null : t.getCanonicalRun().getId(),
                latestRun == null ? null : latestRun.getId(),
                latestRun == null ? null : latestRun.getStatus(),
                latestRun == null ? null : latestRun.getError());
    }
}
