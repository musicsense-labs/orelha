package dev.rifflab.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    List<Album> findByArtistIdOrderByYearAscTitleAsc(Long artistId);

    java.util.Optional<Album> findFirstByArtistIdAndTitleIgnoreCase(Long artistId, String title);
}
