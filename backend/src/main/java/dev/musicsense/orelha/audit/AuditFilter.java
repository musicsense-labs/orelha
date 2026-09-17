package dev.musicsense.orelha.audit;

import dev.musicsense.orelha.common.RemoteAccess;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registra quem entrou e o que fez, depois da resposta: ENTER = {@code GET /api/access} (a UI chama ao
 * abrir), OPEN_TRACK = {@code GET /api/tracks/{id}/timeline} (a tela da faixa), ACTION = qualquer
 * POST/PUT/DELETE em /api. Leituras de apoio (notas, beats, stems, áudio, polling da lista) não entram —
 * seriam ruído sem informação sobre o usuário. Falha ao gravar nunca derruba a requisição.
 */
@Component
public class AuditFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuditFilter.class);
    private static final Pattern TRACK = Pattern.compile("^/api/tracks/(\\d+)(?:/.*)?$");

    private final AuditService audit;

    AuditFilter(AuditService audit) {
        this.audit = audit;
    }

    static AuditEvent.Kind kindOf(String method, String path) {
        if (!path.startsWith("/api/")) {
            return null;
        }
        if ("GET".equals(method)) {
            if (path.equals("/api/access")) {
                return AuditEvent.Kind.ENTER;
            }
            return TRACK.matcher(path).matches() && path.endsWith("/timeline") ? AuditEvent.Kind.OPEN_TRACK : null;
        }
        return "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) ? AuditEvent.Kind.ACTION : null;
    }

    static Long trackIdOf(String path) {
        Matcher m = TRACK.matcher(path);
        return m.matches() ? Long.valueOf(m.group(1)) : null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return kindOf(request.getMethod(), request.getRequestURI()) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            try {
                String path = request.getRequestURI();
                boolean remote = RemoteAccess.isRemote(request);
                String email = RemoteAccess.email(request);
                String ua = request.getHeader("User-Agent");
                audit.record(new AuditEvent(
                        email != null ? email : (remote ? "remoto sem e-mail" : "local"), remote,
                        remote ? request.getHeader(RemoteAccess.ORIGIN_IP_HEADER) : request.getRemoteAddr(),
                        kindOf(request.getMethod(), path), request.getMethod(), path, response.getStatus(),
                        (int) ((System.nanoTime() - started) / 1_000_000), trackIdOf(path),
                        ua == null ? null : ua.substring(0, Math.min(200, ua.length()))));
            } catch (RuntimeException e) {
                log.warn("audit: não gravou {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            }
        }
    }
}
