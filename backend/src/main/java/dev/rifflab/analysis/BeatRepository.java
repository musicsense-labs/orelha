package dev.rifflab.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BeatRepository extends JpaRepository<Beat, Long> {

    List<Beat> findByRunIdOrderByBeatNo(long runId);
}
