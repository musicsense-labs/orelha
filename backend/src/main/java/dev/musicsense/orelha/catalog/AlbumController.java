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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumRepository albums;
    private final ArtistRepository artists;

    AlbumController(AlbumRepository albums, ArtistRepository artists) {
        this.albums = albums;
        this.artists = artists;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<AlbumResponse> list(@RequestParam(required = false) Long artistId) {
        List<Album> result = artistId == null ? albums.findAll() : albums.findByArtistIdOrderByYearAscTitleAsc(artistId);
        return result.stream().map(AlbumResponse::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    AlbumResponse get(@PathVariable Long id) {
        return AlbumResponse.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    AlbumResponse create(@Valid @RequestBody AlbumRequest req) {
        Artist artist = artists.findById(req.artistId())
                .orElseThrow(() -> new NotFoundException("Artist", req.artistId()));
        return AlbumResponse.of(albums.save(new Album(artist, req.title(), req.year())));
    }

    @PutMapping("/{id}")
    @Transactional
    AlbumResponse update(@PathVariable Long id, @Valid @RequestBody AlbumRequest req) {
        Album album = find(id);
        if (!album.getArtist().getId().equals(req.artistId())) {
            throw new IllegalArgumentException("An album cannot be moved to another artist");
        }
        album.setTitle(req.title());
        album.setYear(req.year());
        return AlbumResponse.of(album);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        albums.delete(find(id));
    }

    private Album find(Long id) {
        return albums.findById(id).orElseThrow(() -> new NotFoundException("Album", id));
    }
}
