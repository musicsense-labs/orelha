-- Dois dados que só eram gravados (2026-09-30). chroma_low alimentava o PowerChordDetector, desligado desde
-- 2026-09-14: sob distorção a intermodulação imita a terça, e o chroma não separa power chord de tríade.
-- features_path apontava para o Parquet das séries de timbre por frame, que nada lia (o agregado vai para
-- timbre_summary). O extrator 0.7.0 não produz nenhum dos dois.
ALTER TABLE chord_segment DROP COLUMN chroma_low;
ALTER TABLE analysis_run DROP COLUMN features_path;
