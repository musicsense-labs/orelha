-- A letra pode ser corrigida pelo dono: trechos MANUAL convivem com os do extrator no mesmo run e a
-- leitura prefere MANUAL (como key_segment e section). Palavras manuais não têm probabilidade.
ALTER TABLE lyric_segment ADD COLUMN source TEXT NOT NULL DEFAULT 'EXTRACTOR';

DROP INDEX lyric_segment_run_idx;
CREATE INDEX lyric_segment_run_idx ON lyric_segment (analysis_run_id, source, start_s);
