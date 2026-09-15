package dev.musicsense.orelha.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionRepository extends JpaRepository<Section, Long> {

    List<Section> findByRunIdAndSourceOrderByPosition(long runId, SectionSource source);

    void deleteByRunIdAndSource(long runId, SectionSource source);

    /** As partes da fonte preferida do run: MANUAL > EXTRACTOR > DERIVED; vazio se nenhuma fonte tem partes. */
    default List<Section> findPreferred(long runId) {
        SectionSource[] sources = SectionSource.values();
        for (int i = sources.length - 1; i >= 0; i--) {
            List<Section> found = findByRunIdAndSourceOrderByPosition(runId, sources[i]);
            if (!found.isEmpty()) {
                return found;
            }
        }
        return List.of();
    }
}
