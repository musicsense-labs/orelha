package dev.rifflab.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findByAlbumIdOrderByTrackNoAsc(Long albumId);
}
