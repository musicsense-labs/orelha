package dev.musicsense.orelha.analysis;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {

    long countByStatus(RunStatus status);

    /** Faixas cujo run mais recente falhou (um run antigo falho de faixa já reanalisada não conta). */
    @Query(value = """
            SELECT count(*) FROM analysis_run
            WHERE status = 'FAILED' AND id IN (SELECT max(id) FROM analysis_run GROUP BY track_id)
            """, nativeQuery = true)
    long countLatestFailed();

    @Modifying
    @Query(value = """
            UPDATE analysis_run
            SET status = 'QUEUED', attempts = 0, error = NULL, locked_at = NULL, started_at = NULL, finished_at = NULL
            WHERE status = 'FAILED' AND id IN (SELECT max(id) FROM analysis_run GROUP BY track_id)
            """, nativeQuery = true)
    int requeueLatestFailed();

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

    /**
     * Runs que dizem estar em execução mas cujo batimento (locked_at) parou: o worker que os reivindicou
     * morreu. Lidos com FOR UPDATE SKIP LOCKED para nunca disputar um run que outro worker esteja tocando.
     */
    @Query(value = """
            SELECT * FROM analysis_run
            WHERE status = 'RUNNING' AND (locked_at IS NULL OR locked_at < :deadline)
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<AnalysisRun> lockStaleRunning(java.time.Instant deadline);

    /** Só o batimento, sem carregar o run: chamado durante a extração, que leva minutos. */
    @Modifying
    @Query("update AnalysisRun r set r.lockedAt = :now where r.id = :runId and r.status = 'RUNNING'")
    int touch(long runId, java.time.Instant now);

    /**
     * Os runs de uma faixa travados (FOR UPDATE) até o fim da transação: o SKIP LOCKED do worker pula
     * o que está sendo removido, e um run que o worker já reivindicou aparece como RUNNING.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AnalysisRun r where r.track.id = :trackId")
    List<AnalysisRun> lockByTrackId(long trackId);
}
