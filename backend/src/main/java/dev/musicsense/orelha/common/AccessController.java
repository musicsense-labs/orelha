package dev.musicsense.orelha.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Como a UI foi aberta: remota (pelo túnel, com o e-mail do Access) ou local; decide o que ela mostra. */
@RestController
public class AccessController {

    public record AccessInfo(boolean remote, String email, boolean importFolderAllowed) {
    }

    @GetMapping("/api/access")
    AccessInfo access(HttpServletRequest request) {
        boolean remote = RemoteAccess.isRemote(request);
        return new AccessInfo(remote, RemoteAccess.email(request), !remote);
    }
}
