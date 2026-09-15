package dev.musicsense.orelha.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VocalNoteRepository extends JpaRepository<VocalNote, Long> {

    List<VocalNote> findByRunIdOrderByStartS(long runId);
}
