package dev.musicsense.orelha.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Grava o evento numa transação própria: a auditoria nunca participa (nem desfaz) a transação da ação. */
@Service
public class AuditService {

    private final AuditEventRepository events;

    AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEvent event) {
        events.save(event);
    }
}
