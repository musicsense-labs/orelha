-- Letra sincronizada que veio ao lado do áudio (.lrc do app do dono). É dado externo e cru, por faixa e não
-- por run: a re-análise não a perde, e o `LyricMerger` a usa para corrigir o texto do ASR mantendo os tempos
-- por palavra do Whisper. O carimbo marca só o início do verso — o formato não tem fim de linha.
CREATE TABLE lrc_line (
    id       BIGSERIAL PRIMARY KEY,
    track_id BIGINT        NOT NULL REFERENCES track (id) ON DELETE CASCADE,
    line_no  INTEGER       NOT NULL,          -- ordem no arquivo, para reconstruir sem depender do tempo
    start_s  NUMERIC(9, 3) NOT NULL,
    text     TEXT          NOT NULL,
    UNIQUE (track_id, line_no)
);

CREATE INDEX lrc_line_track_start_idx ON lrc_line (track_id, start_s);
