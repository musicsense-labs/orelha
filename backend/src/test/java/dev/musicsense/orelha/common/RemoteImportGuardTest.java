package dev.musicsense.orelha.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteImportGuardTest {

    private final RemoteImportGuard guard = new RemoteImportGuard();

    @Test
    void importPathsAreTheOnlyOnesGuarded() {
        assertThat(RemoteImportGuard.blocked("/api/tracks/import")).isTrue();
        assertThat(RemoteImportGuard.blocked("/api/tracks/import/stage")).isTrue();
        assertThat(RemoteImportGuard.blocked("/api/tracks/import/confirm")).isTrue();
        assertThat(RemoteImportGuard.blocked("/api/tracks/import-path/preview")).isTrue();
        assertThat(RemoteImportGuard.blocked("/api/tracks/upload")).isFalse();
        assertThat(RemoteImportGuard.blocked("/api/tracks/4/lyrics")).isFalse();
    }

    @Test
    void remoteImportIsForbiddenLocalPasses() throws Exception {
        MockHttpServletRequest remote = new MockHttpServletRequest("POST", "/api/tracks/import/stage");
        remote.addHeader(RemoteAccess.EMAIL_HEADER, "alguem@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        guard.doFilter(remote, response, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("no próprio PC");
        assertThat(chain.getRequest()).isNull();   // não chegou ao controller

        MockHttpServletRequest local = new MockHttpServletRequest("POST", "/api/tracks/import/stage");
        MockHttpServletResponse ok = new MockHttpServletResponse();
        MockFilterChain passed = new MockFilterChain();
        guard.doFilter(local, ok, passed);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(passed.getRequest()).isNotNull();
    }

    @Test
    void tunnelWithoutAccessEmailStillCountsAsRemote() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RemoteAccess.ORIGIN_IP_HEADER, "203.0.113.9");
        assertThat(RemoteAccess.isRemote(request)).isTrue();
        assertThat(RemoteAccess.email(request)).isNull();
    }
}
