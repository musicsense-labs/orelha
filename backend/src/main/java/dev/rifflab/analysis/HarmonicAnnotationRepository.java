package dev.rifflab.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HarmonicAnnotationRepository extends JpaRepository<HarmonicAnnotation, Long> {

    @Query("""
            select a from HarmonicAnnotation a
            join fetch a.segment s
            left join fetch a.keySegment
            where s.run.id = :runId and a.normalizerVersion = :version
            order by s.seqNo
            """)
    List<HarmonicAnnotation> findTimeline(long runId, String version);
}
