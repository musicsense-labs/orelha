package dev.musicsense.orelha.audit;

import dev.musicsense.orelha.audit.AuditEventRepository.ActorSummary;
import dev.musicsense.orelha.common.AdminProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

/** Auditoria, só para o administrador ({@link AdminProperties}); qualquer outro recebe 403. */
@RestController
@EnableConfigurationProperties(AdminProperties.class)
@RequestMapping("/api/admin/audit")
public class AdminController {

    public record EventView(Long id, Instant at, String actor, boolean remote, String ip, AuditEvent.Kind kind,
                            String method, String path, int status, Integer durationMs, Long trackId, String userAgent) {
        static EventView of(AuditEvent e) {
            return new EventView(e.getId(), e.getAt(), e.getActor(), e.isRemote(), e.getIp(), e.getKind(), e.getMethod(),
                    e.getPath(), e.getStatus(), e.getDurationMs(), e.getTrackId(), e.getUserAgent());
        }
    }

    private final AuditEventRepository events;
    private final AdminProperties admin;

    AdminController(AuditEventRepository events, AdminProperties admin) {
        this.events = events;
        this.admin = admin;
    }

    /** Eventos mais recentes primeiro; {@code before} = id do último recebido, para paginar; {@code actor} filtra. */
    @GetMapping
    @Transactional(readOnly = true)
    List<EventView> events(HttpServletRequest request, @RequestParam(required = false) String actor,
                           @RequestParam(required = false) Long before, @RequestParam(defaultValue = "200") int limit) {
        requireAdmin(request);
        long beforeId = before == null ? Long.MAX_VALUE : before;
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 1000));
        List<AuditEvent> found = actor == null || actor.isBlank()
                ? events.findByIdLessThanOrderByIdDesc(beforeId, page)
                : events.findByActorAndIdLessThanOrderByIdDesc(actor, beforeId, page);
        return found.stream().map(EventView::of).toList();
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    List<ActorSummary> users(HttpServletRequest request) {
        requireAdmin(request);
        return events.summarizeActors(AuditEvent.Kind.ACTION);
    }

    private void requireAdmin(HttpServletRequest request) {
        if (!admin.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Só o administrador vê a auditoria.");
        }
    }
}
