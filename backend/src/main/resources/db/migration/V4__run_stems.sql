-- Stems persistidos pelo extrator (volume bind-mounted): {"bass": "/data/stems/<sha>/bass.wav", ...}.
-- O backend traduz o caminho do container para o host e serve o áudio ao player multi-stem.
ALTER TABLE analysis_run ADD COLUMN stems JSONB;
