package dev.rifflab.catalog;

import java.math.BigDecimal;

public record TrackResponse(Long id, Long albumId, String title, Integer trackNo, BigDecimal durationS,
                            String audioPath, String audioSha256, Integer sampleRate, Long canonicalRunId) {

    static TrackResponse of(Track t) {
        return new TrackResponse(t.getId(), t.getAlbum().getId(), t.getTitle(), t.getTrackNo(), t.getDurationS(),
                t.getAudioPath(), t.getAudioSha256(), t.getSampleRate(),
                t.getCanonicalRun() == null ? null : t.getCanonicalRun().getId());
    }
}
