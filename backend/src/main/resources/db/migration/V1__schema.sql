-- orelha — schema inicial.
-- Convenções: pitch class (pc) = 0..11 com C = 0; tempos em segundos (NUMERIC(9,3)).
-- Dado BRUTO (o que o extrator devolveu) e dado DERIVADO (o que o HarmonicNormalizer
-- calculou) nunca ficam na mesma tabela: chord_segment é bruto, harmonic_annotation é derivado.

-- ---------------------------------------------------------------------------
-- Catálogo
-- ---------------------------------------------------------------------------
CREATE TABLE artist (
    id           BIGSERIAL PRIMARY KEY,
    name         TEXT    NOT NULL UNIQUE,
    country      TEXT,
    formed_year  INTEGER
);

CREATE TABLE album (
    id         BIGSERIAL PRIMARY KEY,
    artist_id  BIGINT  NOT NULL REFERENCES artist (id),
    title      TEXT    NOT NULL,
    year       INTEGER,
    UNIQUE (artist_id, title)
);

CREATE TABLE track (
    id                BIGSERIAL PRIMARY KEY,
    album_id          BIGINT   NOT NULL REFERENCES album (id),
    title             TEXT     NOT NULL,
    track_no          INTEGER,
    duration_s        NUMERIC(9, 3),
    audio_path        TEXT     NOT NULL,
    audio_sha256      VARCHAR(64) NOT NULL CHECK (length(audio_sha256) = 64),  -- identidade dos bytes
    sample_rate       INTEGER,
    canonical_run_id  BIGINT,                     -- FK adicionada após analysis_run
    UNIQUE (album_id, track_no)
);

-- ---------------------------------------------------------------------------
-- Proveniência e fila
-- ---------------------------------------------------------------------------
-- Um run = uma intenção de análise + seu resultado. A fila é esta tabela:
-- o poller pega QUEUED com SELECT ... FOR UPDATE SKIP LOCKED.
CREATE TABLE analysis_run (
    id                 BIGSERIAL PRIMARY KEY,
    track_id           BIGINT      NOT NULL REFERENCES track (id),
    extractor_name     TEXT        NOT NULL,
    extractor_version  TEXT,
    model_names        JSONB,                     -- {"chords": "btc-pl", "beats": "beat-transformer", ...}
    status             TEXT        NOT NULL DEFAULT 'QUEUED'
                       CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    attempts           INTEGER     NOT NULL DEFAULT 0,
    locked_at          TIMESTAMPTZ,
    error              TEXT,
    features_path      TEXT,                      -- Parquet com séries por frame (fora do banco)
    requested_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ
);

CREATE INDEX analysis_run_queue_idx ON analysis_run (status, requested_at);
CREATE INDEX analysis_run_track_idx ON analysis_run (track_id);

ALTER TABLE track
    ADD CONSTRAINT track_canonical_run_fk FOREIGN KEY (canonical_run_id) REFERENCES analysis_run (id);

-- ---------------------------------------------------------------------------
-- Resultado bruto da extração (uma linha ou série por run)
-- ---------------------------------------------------------------------------
CREATE TABLE track_analysis (
    id               BIGSERIAL PRIMARY KEY,
    analysis_run_id  BIGINT NOT NULL UNIQUE REFERENCES analysis_run (id),
    bpm              NUMERIC(6, 2),
    time_signature   TEXT,                        -- '4/4', '6/8'
    integrated_lufs  NUMERIC(6, 2)                -- para normalizar comparações tímbricas entre masterizações
);

-- Tonalidade por trecho. Tonalidade global = um segmento cobrindo a faixa inteira.
CREATE TABLE key_segment (
    id               BIGSERIAL PRIMARY KEY,
    analysis_run_id  BIGINT   NOT NULL REFERENCES analysis_run (id),
    start_s          NUMERIC(9, 3) NOT NULL,
    end_s            NUMERIC(9, 3) NOT NULL,
    tonic_pc         INTEGER  NOT NULL CHECK (tonic_pc BETWEEN 0 AND 11),
    mode             TEXT     NOT NULL
                     CHECK (mode IN ('MAJOR', 'MINOR', 'DORIAN', 'PHRYGIAN', 'LYDIAN',
                                     'MIXOLYDIAN', 'AEOLIAN', 'LOCRIAN')),
    confidence       REAL,
    source           TEXT     NOT NULL CHECK (source IN ('EXTRACTOR', 'DERIVED', 'MANUAL')),
    CHECK (end_s > start_s)
);

CREATE INDEX key_segment_run_time_idx ON key_segment (analysis_run_id, start_s);

CREATE TABLE beat (
    id               BIGSERIAL PRIMARY KEY,
    analysis_run_id  BIGINT  NOT NULL REFERENCES analysis_run (id),
    beat_no          INTEGER NOT NULL,
    time_s           NUMERIC(9, 3) NOT NULL,
    bar_no           INTEGER,
    is_downbeat      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (analysis_run_id, beat_no)
);

-- Segmento de acorde exatamente como o extrator devolveu, já traduzido para o nosso
-- vocabulário de quality. root_pc é NULL para NO_CHORD/UNKNOWN. chroma[12] é a
-- evidência para o Java decidir POWER (ausência de terça) — nunca inferir pela tonalidade.
CREATE TABLE chord_segment (
    id               BIGSERIAL PRIMARY KEY,
    analysis_run_id  BIGINT   NOT NULL REFERENCES analysis_run (id),
    seq_no           INTEGER  NOT NULL,
    start_s          NUMERIC(9, 3) NOT NULL,
    end_s            NUMERIC(9, 3) NOT NULL,
    root_pc          INTEGER  CHECK (root_pc BETWEEN 0 AND 11),
    quality          TEXT     NOT NULL
                     CHECK (quality IN ('MAJ', 'MIN', 'DIM', 'AUG', 'MAJ6', 'MIN6',
                                        'MAJ7', 'MIN7', 'DOM7', 'DIM7', 'HDIM7', 'MINMAJ7',
                                        'SUS2', 'SUS4', 'POWER', 'NO_CHORD', 'UNKNOWN')),
    bass_pc          INTEGER  CHECK (bass_pc BETWEEN 0 AND 11),
    confidence       REAL,
    chroma           REAL[]   CHECK (chroma IS NULL OR array_length(chroma, 1) = 12),
    UNIQUE (analysis_run_id, seq_no),
    CHECK (end_s > start_s),
    CHECK ((quality IN ('NO_CHORD', 'UNKNOWN')) = (root_pc IS NULL))
);

CREATE INDEX chord_segment_run_time_idx ON chord_segment (analysis_run_id, start_s);

-- Notas do stem de baixo transcritas para MIDI.
CREATE TABLE bass_note (
    id               BIGSERIAL PRIMARY KEY,
    analysis_run_id  BIGINT   NOT NULL REFERENCES analysis_run (id),
    start_s          NUMERIC(9, 3) NOT NULL,
    end_s            NUMERIC(9, 3) NOT NULL,
    midi_pitch       INTEGER  NOT NULL CHECK (midi_pitch BETWEEN 0 AND 127),
    velocity         INTEGER  CHECK (velocity BETWEEN 0 AND 127),
    CHECK (end_s > start_s)
);

CREATE INDEX bass_note_run_time_idx ON bass_note (analysis_run_id, start_s);

-- Agregados tímbricos por stem. As séries por frame ficam no Parquet (analysis_run.features_path).
CREATE TABLE timbre_summary (
    id               BIGSERIAL PRIMARY KEY,
    analysis_run_id  BIGINT NOT NULL REFERENCES analysis_run (id),
    stem_model       TEXT   NOT NULL,             -- 'htdemucs', 'htdemucs_6s', ...
    stem_name        TEXT   NOT NULL,             -- 'bass', 'other', 'guitar', ...
    centroid_mean    REAL,
    centroid_std     REAL,
    flatness_mean    REAL,
    rolloff_p95      REAL,
    rms_mean         REAL,
    UNIQUE (analysis_run_id, stem_model, stem_name)
);

-- ---------------------------------------------------------------------------
-- Derivado pelo HarmonicNormalizer (Java). Versionado: re-derivar não re-extrai.
-- ---------------------------------------------------------------------------
CREATE TABLE harmonic_annotation (
    id                  BIGSERIAL PRIMARY KEY,
    chord_segment_id    BIGINT   NOT NULL REFERENCES chord_segment (id),
    normalizer_version  TEXT     NOT NULL,
    key_segment_id      BIGINT   REFERENCES key_segment (id),   -- tonalidade de referência usada
    degree_interval     INTEGER  CHECK (degree_interval BETWEEN 0 AND 11),
    degree_label        TEXT,                                   -- render: 'I', 'bVI', 'vii°'
    function_class      TEXT     NOT NULL,                      -- vocabulário definido na Onda 1 (P1)
    is_inverted         BOOLEAN  NOT NULL DEFAULT FALSE,
    effective_bass_pc   INTEGER  CHECK (effective_bass_pc BETWEEN 0 AND 11),
    relation_from_prev  TEXT,                                   -- P, L, R, HEXATONIC_POLE, ... (P1)
    UNIQUE (chord_segment_id, normalizer_version)
);
