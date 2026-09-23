package dev.musicsense.orelha.lyrics;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LrcLineRepository extends JpaRepository<LrcLine, Long> {

    List<LrcLine> findByTrackIdOrderByLineNo(long trackId);

    boolean existsByTrackId(long trackId);

    void deleteByTrackId(long trackId);
}
