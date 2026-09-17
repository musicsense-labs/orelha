package dev.musicsense.orelha.audit;

import dev.musicsense.orelha.common.RemoteAccess;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFilterTest {

    @Test
    void onlyEntriesTrackOpeningsAndMutationsAreAudited() {
        assertThat(AuditFilter.kindOf("GET", "/api/access")).isEqualTo(AuditEvent.Kind.ENTER);
        assertThat(AuditFilter.kindOf("GET", "/api/tracks/4/timeline")).isEqualTo(AuditEvent.Kind.OPEN_TRACK);
        assertThat(AuditFilter.kindOf("PUT", "/api/tracks/4/lyrics")).isEqualTo(AuditEvent.Kind.ACTION);
        assertThat(AuditFilter.kindOf("POST", "/api/tracks/upload")).isEqualTo(AuditEvent.Kind.ACTION);
        assertThat(AuditFilter.kindOf("DELETE", "/api/tracks/4")).isEqualTo(AuditEvent.Kind.ACTION);
        assertThat(AuditFilter.kindOf("GET", "/api/tracks")).isNull();              // polling da lista
        assertThat(AuditFilter.kindOf("GET", "/api/tracks/4/vocal-notes")).isNull();
        assertThat(AuditFilter.kindOf("GET", "/tracks/4")).isNull();                // SPA
        assertThat(AuditFilter.trackIdOf("/api/tracks/42/sections")).isEqualTo(42L);
        assertThat(AuditFilter.trackIdOf("/api/artists/1")).isNull();
    }

    @Test
    void recordsActorFromAccessHeadersAfterTheResponse() throws Exception {
        List<AuditEvent> saved = new ArrayList<>();
        AuditFilter filter = new AuditFilter(new AuditService(null) {
            @Override
            public void record(AuditEvent event) {
                saved.add(event);
            }
        });
        MockHttpServletRequest remote = new MockHttpServletRequest("PUT", "/api/tracks/4/key");
        remote.addHeader(RemoteAccess.EMAIL_HEADER, "amigo@example.com");
        remote.addHeader(RemoteAccess.ORIGIN_IP_HEADER, "203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        filter.doFilter(remote, response, new MockFilterChain());
        assertThat(saved).hasSize(1);
        AuditEvent e = saved.get(0);
        assertThat(e.getActor()).isEqualTo("amigo@example.com");
        assertThat(e.isRemote()).isTrue();
        assertThat(e.getIp()).isEqualTo("203.0.113.9");
        assertThat(e.getKind()).isEqualTo(AuditEvent.Kind.ACTION);
        assertThat(e.getTrackId()).isEqualTo(4L);
        assertThat(e.getStatus()).isEqualTo(200);

        MockHttpServletRequest local = new MockHttpServletRequest("GET", "/api/access");
        filter.doFilter(local, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(saved.get(1).getActor()).isEqualTo("local");
        assertThat(saved.get(1).getKind()).isEqualTo(AuditEvent.Kind.ENTER);
        assertThat(saved.get(1).isRemote()).isFalse();
    }
}
