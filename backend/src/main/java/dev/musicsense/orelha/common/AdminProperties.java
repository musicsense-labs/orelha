package dev.musicsense.orelha.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * orelha.admin.emails — quem vê a aba do administrador (auditoria). O acesso local (sem túnel) é sempre
 * do dono e conta como administrador.
 */
@ConfigurationProperties("orelha.admin")
public record AdminProperties(@DefaultValue("dfcsantos@gmail.com") List<String> emails) {

    public boolean isAdmin(HttpServletRequest request) {
        if (!RemoteAccess.isRemote(request)) {
            return true;
        }
        String email = RemoteAccess.email(request);
        return email != null && emails.stream().anyMatch(e -> e.equalsIgnoreCase(email));
    }
}
