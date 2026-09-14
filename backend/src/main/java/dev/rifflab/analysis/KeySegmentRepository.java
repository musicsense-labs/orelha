package dev.rifflab.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface KeySegmentRepository extends JpaRepository<KeySegment, Long> {

    List<KeySegment> findByRunIdOrderByIdDesc(long runId);

    /** Tonalidade de referência preferida para as leituras de um run: MANUAL > DERIVED > EXTRACTOR, a mais recente. */
    default Optional<KeySegment> findPreferred(long runId) {
        return findByRunIdOrderByIdDesc(runId).stream()
                .min(Comparator.comparingInt((KeySegment k) -> k.getSource().ordinal()).reversed()
                        .thenComparing(KeySegment::getId, Comparator.reverseOrder()));
    }
}
