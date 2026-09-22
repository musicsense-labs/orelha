package dev.musicsense.orelha.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    /** Do mais antigo para o mais novo pelo ano que vale como era (primeira edição, ou a tag enquanto não há). */
    @Query("select a from Album a where a.artist.id = :artistId order by coalesce(a.firstReleased, a.year) asc nulls last, a.title asc")
    List<Album> findByArtistOrderedByEra(Long artistId);

    java.util.Optional<Album> findFirstByArtistIdAndTitleIgnoreCase(Long artistId, String title);
}
