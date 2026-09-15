package dev.musicsense.orelha.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TrackAnalysisRepository extends JpaRepository<TrackAnalysis, Long> {

    Optional<TrackAnalysis> findByRunId(long runId);
}
