package dev.rifflab.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, Long> {

    java.util.Optional<Artist> findFirstByNameIgnoreCase(String name);
}
