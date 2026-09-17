package dev.musicsense.orelha.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByIdLessThanOrderByIdDesc(long beforeId, Pageable page);

    List<AuditEvent> findByActorAndIdLessThanOrderByIdDesc(String actor, long beforeId, Pageable page);

    /** Um resumo por usuário: primeira e última vez visto, total de eventos, de ações e de eventos remotos. */
    record ActorSummary(String actor, Instant firstSeen, Instant lastSeen, long events, long actions, long remoteEvents) {
    }

    @Query("""
            select new dev.musicsense.orelha.audit.AuditEventRepository$ActorSummary(
                e.actor, min(e.at), max(e.at), count(e),
                sum(case when e.kind = :action then 1L else 0L end),
                sum(case when e.remote = true then 1L else 0L end))
            from AuditEvent e group by e.actor order by max(e.at) desc""")
    List<ActorSummary> summarizeActors(@Param("action") AuditEvent.Kind action);
}
