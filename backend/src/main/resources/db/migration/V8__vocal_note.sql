-- Notas do stem de voz transcritas para MIDI (basic-pitch, extrator 0.5.0). Mesma forma de bass_note.
CREATE TABLE vocal_note (
    id              BIGSERIAL PRIMARY KEY,
    analysis_run_id BIGINT       NOT NULL REFERENCES analysis_run (id) ON DELETE CASCADE,
    start_s         NUMERIC(9,3) NOT NULL,
    end_s           NUMERIC(9,3) NOT NULL,
    midi_pitch      INTEGER      NOT NULL,
    velocity        INTEGER
);

CREATE INDEX vocal_note_run_idx ON vocal_note (analysis_run_id, start_s);
