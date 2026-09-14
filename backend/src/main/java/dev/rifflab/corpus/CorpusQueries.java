package dev.rifflab.corpus;

import dev.rifflab.corpus.HarmonicProfile.AlbumTimbre;
import dev.rifflab.harmony.ChordQuality;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * As duas queries do corpus. Só entram runs canônicos; a tonalidade de cada run é a preferida
 * (MANUAL > DERIVED > EXTRACTOR, a mais recente) — a mesma regra da timeline.
 */
@Repository
class CorpusQueries {

    private static final String ANNOTATED_SEGMENTS = """
            WITH preferred_key AS (
                SELECT DISTINCT ON (analysis_run_id) analysis_run_id, id AS key_segment_id
                FROM key_segment
                ORDER BY analysis_run_id,
                         CASE source WHEN 'MANUAL' THEN 3 WHEN 'DERIVED' THEN 2 ELSE 1 END DESC, id DESC
            )
            SELECT t.id, t.title, s.seq_no, s.start_s, s.end_s, s.root_pc, s.quality,
                   a.degree_interval, a.degree_label, a.function_class, a.relation_from_prev, a.effective_bass_pc
            FROM track t
            JOIN analysis_run r ON r.id = t.canonical_run_id
            JOIN preferred_key pk ON pk.analysis_run_id = r.id
            JOIN chord_segment s ON s.analysis_run_id = r.id
            JOIN harmonic_annotation a ON a.chord_segment_id = s.id
                                      AND a.key_segment_id = pk.key_segment_id
                                      AND a.normalizer_version = :version
            WHERE t.id IN (:trackIds)
            ORDER BY t.id, s.seq_no
            """;

    private static final String TIMBRE_BY_ALBUM = """
            SELECT al.id, al.title, al.year, ts.stem_name, COUNT(*),
                   AVG(ts.centroid_mean), AVG(ts.centroid_std), AVG(ts.flatness_mean), AVG(ts.rolloff_p95), AVG(ts.rms_mean)
            FROM track t
            JOIN album al ON al.id = t.album_id
            JOIN timbre_summary ts ON ts.analysis_run_id = t.canonical_run_id
            WHERE t.id IN (:trackIds)
            GROUP BY al.id, al.title, al.year, ts.stem_name
            ORDER BY al.year NULLS LAST, al.title, ts.stem_name
            """;

    private final EntityManager em;

    CorpusQueries(EntityManager em) {
        this.em = em;
    }

    @SuppressWarnings("unchecked")
    List<AnnotatedSegment> annotatedSegments(Collection<Long> trackIds, String normalizerVersion) {
        if (trackIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery(ANNOTATED_SEGMENTS)
                .setParameter("trackIds", trackIds)
                .setParameter("version", normalizerVersion)
                .getResultList();
        return rows.stream().map(r -> new AnnotatedSegment(
                ((Number) r[0]).longValue(), (String) r[1], ((Number) r[2]).intValue(),
                ((BigDecimal) r[3]).doubleValue(), ((BigDecimal) r[4]).doubleValue(),
                integer(r[5]), ChordQuality.valueOf((String) r[6]),
                integer(r[7]), (String) r[8], (String) r[9], (String) r[10], integer(r[11]))).toList();
    }

    @SuppressWarnings("unchecked")
    List<AlbumTimbre> timbreByAlbum(Collection<Long> trackIds) {
        if (trackIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery(TIMBRE_BY_ALBUM).setParameter("trackIds", trackIds).getResultList();
        return rows.stream().map(r -> new AlbumTimbre(
                ((Number) r[0]).longValue(), (String) r[1], integer(r[2]), (String) r[3], ((Number) r[4]).intValue(),
                dbl(r[5]), dbl(r[6]), dbl(r[7]), dbl(r[8]), dbl(r[9]))).toList();
    }

    private static Integer integer(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static Double dbl(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }
}
