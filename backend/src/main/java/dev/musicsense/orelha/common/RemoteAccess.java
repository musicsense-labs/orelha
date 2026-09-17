package dev.musicsense.orelha.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * De onde veio a requisição: pelo túnel do Cloudflare (o Access injeta o e-mail autenticado e o
 * cloudflared injeta o IP de origem) ou direto no PC do dono (localhost, sem esses cabeçalhos).
 * Um cliente remoto não consegue remover os cabeçalhos — a borda os sobrescreve; um cliente local
 * poderia adicioná-los e só se restringiria a si mesmo.
 */
public final class RemoteAccess {

    public static final String EMAIL_HEADER = "Cf-Access-Authenticated-User-Email";
    public static final String ORIGIN_IP_HEADER = "Cf-Connecting-Ip";

    private RemoteAccess() {
    }

    public static boolean isRemote(HttpServletRequest request) {
        return request.getHeader(EMAIL_HEADER) != null || request.getHeader(ORIGIN_IP_HEADER) != null;
    }

    /** E-mail autenticado pelo Access; null em acesso local. */
    public static String email(HttpServletRequest request) {
        return request.getHeader(EMAIL_HEADER);
    }
}
