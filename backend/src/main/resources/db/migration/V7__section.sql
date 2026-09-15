-- Partes da música (A, B, C… ou nomes dados pelo dono). Derivadas por repetição harmônica (DERIVED),
-- vindas de um modelo de estrutura (EXTRACTOR, ainda sem fonte) ou editadas (MANUAL). A leitura
-- prefere MANUAL > EXTRACTOR > DERIVED, como key_segment.
CREATE TABLE section (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT       NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    source          VARCHAR(16)  NOT NULL,
    position        INTEGER      NOT NULL,
    start_s         NUMERIC(9,3) NOT NULL,
    end_s           NUMERIC(9,3) NOT NULL,
    cycle_end_s     NUMERIC(9,3) NOT NULL,   -- fim da primeira repetição do ciclo (= end_s se não há ciclo)
    repeats         INTEGER      NOT NULL DEFAULT 1,
    label           VARCHAR(64)  NOT NULL,
    UNIQUE (analysis_run_id, source, position)
);

CREATE INDEX section_run_idx ON section (analysis_run_id);
