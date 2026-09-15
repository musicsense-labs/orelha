package dev.musicsense.orelha.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
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

    /** O run mais recente de cada faixa (QUEUED, RUNNING, DONE ou FAILED), numa query só. */
    @Query("select r from AnalysisRun r where r.id in (select max(r2.id) from AnalysisRun r2 group by r2.track.id)")
    List<AnalysisRun> findLatestPerTrack();

    Optional<AnalysisRun> findFirstByTrackIdOrderByIdDesc(long trackId);
}
