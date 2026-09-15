package dev.musicsense.orelha.catalog;

import dev.musicsense.orelha.common.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final ArtistRepository artists;

    ArtistController(ArtistRepository artists) {
        this.artists = artists;
    }

    @GetMapping
    List<ArtistResponse> list() {
        return artists.findAll().stream().map(ArtistResponse::of).toList();
    }

    @GetMapping("/{id}")
    ArtistResponse get(@PathVariable Long id) {
        return ArtistResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ArtistResponse create(@Valid @RequestBody ArtistRequest req) {
        return ArtistResponse.of(artists.save(new Artist(req.name(), req.country(), req.formedYear())));
    }

    @PutMapping("/{id}")
    @Transactional
    ArtistResponse update(@PathVariable Long id, @Valid @RequestBody ArtistRequest req) {
        Artist artist = find(id);
        artist.setName(req.name());
        artist.setCountry(req.country());
        artist.setFormedYear(req.formedYear());
        return ArtistResponse.of(artist);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        artists.delete(find(id));
    }

    private Artist find(Long id) {
        return artists.findById(id).orElseThrow(() -> new NotFoundException("Artist", id));
    }
}
