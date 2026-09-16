-- Análise humana de referência por faixa (hoje: TheoryTab do Hooktheory, transcrita pelo dono), para
-- medir o extrator e o derivador de partes. Texto bruto como colado + seções interpretadas (JSON).
CREATE TABLE reference_analysis (
    id         BIGSERIAL PRIMARY KEY,
    track_id   BIGINT    NOT NULL UNIQUE REFERENCES track (id) ON DELETE CASCADE,
    source     TEXT      NOT NULL DEFAULT 'THEORYTAB',
    url        TEXT,
    tonic_pc   INTEGER   CHECK (tonic_pc BETWEEN 0 AND 11),
    mode       TEXT,
    raw_text   TEXT      NOT NULL,
    sections   JSONB     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
