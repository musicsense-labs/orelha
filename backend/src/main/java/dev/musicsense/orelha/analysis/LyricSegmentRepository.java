package dev.musicsense.orelha.analysis;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LyricSegmentRepository extends JpaRepository<LyricSegment, Long> {

    @EntityGraph(attributePaths = "words")
    List<LyricSegment> findByRunIdAndSourceOrderByStartS(long runId, LyricSource source);

    void deleteByRunIdAndSource(long runId, LyricSource source);
}
