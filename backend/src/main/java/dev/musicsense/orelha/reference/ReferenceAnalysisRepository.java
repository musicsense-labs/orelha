package dev.musicsense.orelha.reference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReferenceAnalysisRepository extends JpaRepository<ReferenceAnalysis, Long> {

    Optional<ReferenceAnalysis> findByTrackId(long trackId);
}
