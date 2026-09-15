package dev.musicsense.orelha.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findByAlbumIdOrderByTrackNoAsc(Long albumId);

    List<Track> findByAlbumArtistId(Long artistId);

    boolean existsByAudioSha256(String audioSha256);

    boolean existsByAlbumIdAndTrackNo(Long albumId, Integer trackNo);
}
