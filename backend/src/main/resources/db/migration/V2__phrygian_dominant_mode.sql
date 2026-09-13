-- Frígio dominante (5º modo da menor harmônica) como referência tonal: tônica maior com ♭II.
ALTER TABLE key_segment DROP CONSTRAINT key_segment_mode_check;
ALTER TABLE key_segment ADD CONSTRAINT key_segment_mode_check
    CHECK (mode IN ('MAJOR', 'MINOR', 'DORIAN', 'PHRYGIAN', 'PHRYGIAN_DOMINANT', 'LYDIAN',
                    'MIXOLYDIAN', 'AEOLIAN', 'LOCRIAN'));
