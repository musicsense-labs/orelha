-- Chroma do stem de guitarra restrito a C2–C4: evidência para decidir power chord sem o 5º harmônico
-- da distorção (o chroma da mixagem não separa power chord distorcido de tríade — medido em 2026-09-14).
ALTER TABLE chord_segment
    ADD COLUMN chroma_low REAL[] CHECK (chroma_low IS NULL OR array_length(chroma_low, 1) = 12);

-- Uma anotação por (segmento, versão do normalizer, tonalidade de referência): corrigir a tonalidade
-- (key_segment MANUAL) re-deriva sem re-extrair e sem apagar a leitura feita com a tonalidade do extrator.
ALTER TABLE harmonic_annotation
    DROP CONSTRAINT harmonic_annotation_chord_segment_id_normalizer_version_key;
ALTER TABLE harmonic_annotation
    ADD CONSTRAINT harmonic_annotation_segment_version_key_uq
        UNIQUE NULLS NOT DISTINCT (chord_segment_id, normalizer_version, key_segment_id);

CREATE INDEX harmonic_annotation_key_segment_idx ON harmonic_annotation (key_segment_id);
