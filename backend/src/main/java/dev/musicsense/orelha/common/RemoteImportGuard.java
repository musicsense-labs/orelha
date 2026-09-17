package dev.musicsense.orelha.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Importar pasta só no próprio PC: pelo túnel, o upload de lotes bate no limite de 100 MB do plano e o
 * "importar pasta do servidor" (import-path) lê o disco da máquina do dono — nenhum dos dois é para
 * usuários remotos. Upload de uma faixa (/upload) continua liberado. Responde 403 como ProblemDetail.
 */
@Component
public class RemoteImportGuard extends OncePerRequestFilter {

    static boolean blocked(String path) {
        return path.startsWith("/api/tracks/import/") || path.equals("/api/tracks/import")
                || path.startsWith("/api/tracks/import-path");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !blocked(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!RemoteAccess.isRemote(request)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,"
                + "\"detail\":\"Importar pasta só no próprio PC do acervo; pelo endereço remoto use '+ uma faixa'.\","
                + "\"instance\":\"" + request.getRequestURI() + "\"}");
    }
}
