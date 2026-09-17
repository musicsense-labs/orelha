package dev.musicsense.orelha.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Uma entrada, uma faixa aberta ou uma ação de um usuário; ver {@link AuditFilter}. */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    public enum Kind { ENTER, OPEN_TRACK, ACTION }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant at = Instant.now();

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private boolean remote;

    private String ip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private int status;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "track_id")
    private Long trackId;

    @Column(name = "user_agent")
    private String userAgent;

    protected AuditEvent() {
    }

    public AuditEvent(String actor, boolean remote, String ip, Kind kind, String method, String path, int status,
                      Integer durationMs, Long trackId, String userAgent) {
        this.actor = actor;
        this.remote = remote;
        this.ip = ip;
        this.kind = kind;
        this.method = method;
        this.path = path;
        this.status = status;
        this.durationMs = durationMs;
        this.trackId = trackId;
        this.userAgent = userAgent;
    }

    public Long getId() {
        return id;
    }

    public Instant getAt() {
        return at;
    }

    public String getActor() {
        return actor;
    }

    public boolean isRemote() {
        return remote;
    }

    public String getIp() {
        return ip;
    }

    public Kind getKind() {
        return kind;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public int getStatus() {
        return status;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public Long getTrackId() {
        return trackId;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
