package dev.rifflab.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {

    /** Próximo run da fila; SKIP LOCKED deixa vários workers coexistirem sem disputar a mesma linha. */
    @Query(value = """
            SELECT * FROM analysis_run
            WHERE status = 'QUEUED'
            ORDER BY requested_at
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<AnalysisRun> lockNextQueued();
}
