package dev.musicsense.orelha.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BassNoteRepository extends JpaRepository<BassNote, Long> {

    List<BassNote> findByRunIdOrderByStartS(long runId);
}
