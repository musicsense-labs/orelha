-- Quem entrou e o que fez: um evento por entrada (GET /api/access ao abrir o app), por faixa aberta e por
-- ação (POST/PUT/DELETE em /api). Gravado por AuditFilter; lido só pelo administrador (/api/admin/audit).
CREATE TABLE audit_event (
    id          BIGSERIAL PRIMARY KEY,
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor       TEXT        NOT NULL,          -- e-mail do Access; 'local' quando não veio pelo túnel
    remote      BOOLEAN     NOT NULL,
    ip          TEXT,
    kind        TEXT        NOT NULL,          -- ENTER, OPEN_TRACK, ACTION
    method      TEXT        NOT NULL,
    path        TEXT        NOT NULL,
    status      INTEGER     NOT NULL,
    duration_ms INTEGER,
    track_id    BIGINT,                        -- quando o caminho é de uma faixa
    user_agent  TEXT
);

CREATE INDEX audit_event_at_idx ON audit_event (at DESC);
CREATE INDEX audit_event_actor_idx ON audit_event (actor, at DESC);
