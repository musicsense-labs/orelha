-- Letra por ASR sobre o stem de voz (faster-whisper, extrator 0.6.0). Bruto do extrator: trechos com a
-- probabilidade de "não é fala" e palavras com tempo. A classificação das notas de voz (cantada com
-- texto, cantada sem texto, provável vazamento de outro instrumento) é derivada em Java na leitura.
CREATE TABLE lyric_segment (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT       NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    start_s         NUMERIC(9,3) NOT NULL,
    end_s           NUMERIC(9,3) NOT NULL,
    text            TEXT         NOT NULL,
    no_speech_prob  REAL
);

CREATE INDEX lyric_segment_run_idx ON lyric_segment (analysis_run_id, start_s);

CREATE TABLE lyric_word (
    id               BIGSERIAL PRIMARY KEY,
    lyric_segment_id BIGINT       NOT NULL REFERENCES lyric_segment (id) ON DELETE CASCADE,
    start_s          NUMERIC(9,3) NOT NULL,
    end_s            NUMERIC(9,3) NOT NULL,
    text             TEXT         NOT NULL,
    probability      REAL
);

CREATE INDEX lyric_word_segment_idx ON lyric_word (lyric_segment_id, start_s);

-- Idioma detectado pelo ASR (ISO 639-1) e a confiança da detecção.
ALTER TABLE track_analysis ADD COLUMN lyrics_language TEXT;
ALTER TABLE track_analysis ADD COLUMN lyrics_language_confidence REAL;
