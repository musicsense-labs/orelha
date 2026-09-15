-- Deleting a track removes its runs and everything derived from them.
-- Album/artist stay restrictive on purpose: an album with tracks is not deleted by accident.

ALTER TABLE track DROP CONSTRAINT track_canonical_run_fk,
    ADD CONSTRAINT track_canonical_run_fk FOREIGN KEY (canonical_run_id) REFERENCES analysis_run (id) ON DELETE SET NULL;

ALTER TABLE analysis_run DROP CONSTRAINT analysis_run_track_id_fkey,
    ADD CONSTRAINT analysis_run_track_id_fkey FOREIGN KEY (track_id) REFERENCES track (id) ON DELETE CASCADE;

ALTER TABLE track_analysis DROP CONSTRAINT track_analysis_analysis_run_id_fkey,
    ADD CONSTRAINT track_analysis_analysis_run_id_fkey FOREIGN KEY (analysis_run_id) REFERENCES analysis_run (id) ON DELETE CASCADE;

ALTER TABLE key_segment DROP CONSTRAINT key_segment_analysis_run_id_fkey,
    ADD CONSTRAINT key_segment_analysis_run_id_fkey FOREIGN KEY (analysis_run_id) REFERENCES analysis_run (id) ON DELETE CASCADE;

ALTER TABLE beat DROP CONSTRAINT beat_analysis_run_id_fkey,
    ADD CONSTRAINT beat_analysis_run_id_fkey FOREIGN KEY (analysis_run_id) REFERENCES analysis_run (id) ON DELETE CASCADE;

ALTER TABLE chord_segment DROP CONSTRAINT chord_segment_analysis_run_id_fkey,
    ADD CONSTRAINT chord_segment_analysis_run_id_fkey FOREIGN KEY (analysis_run_id) REFERENCES analysis_run (id) ON DELETE CASCADE;

ALTER TABLE bass_note DROP CONSTRAINT bass_note_analysis_run_id_fkey,
    ADD CONSTRAINT bass_note_analysis_run_id_fkey FOREIGN KEY (analysis_run_id) REFERENCES analysis_run (id) ON DELETE CASCADE;

ALTER TABLE timbre_summary DROP CONSTRAINT timbre_summary_analysis_run_id_fkey,
    ADD CONSTRAINT timbre_summary_analysis_run_id_fkey FOREIGN KEY (analysis_run_id) REFERENCES analysis_run (id) ON DELETE CASCADE;

ALTER TABLE harmonic_annotation DROP CONSTRAINT harmonic_annotation_chord_segment_id_fkey,
    ADD CONSTRAINT harmonic_annotation_chord_segment_id_fkey FOREIGN KEY (chord_segment_id) REFERENCES chord_segment (id) ON DELETE CASCADE;

ALTER TABLE harmonic_annotation DROP CONSTRAINT harmonic_annotation_key_segment_id_fkey,
    ADD CONSTRAINT harmonic_annotation_key_segment_id_fkey FOREIGN KEY (key_segment_id) REFERENCES key_segment (id) ON DELETE CASCADE;
