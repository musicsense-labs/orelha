package dev.musicsense.orelha.metadata;

import dev.musicsense.orelha.catalog.Album;
import dev.musicsense.orelha.catalog.AlbumRepository;
import dev.musicsense.orelha.catalog.MetadataSource;
import dev.musicsense.orelha.common.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Identificação de álbum no MusicBrainz: o backend busca e pontua, o dono escolhe. Nada é gravado sem o PUT,
 * e o título continua sendo o nosso — só o MBID e o ano da primeira edição entram (a era passa a valer por
 * ele; ver {@code Album.effectiveYear}).
 */
@RestController
@RequestMapping("/api/albums/{id}/musicbrainz")
public class MetadataController {

    public record CandidateView(String mbid, String title, String artist, String primaryType, List<String> secondaryTypes,
                                Integer firstReleased, String disambiguation, double score, boolean confident) {
    }

    public record Candidates(String searchedTitle, boolean configured, List<CandidateView> candidates) {
    }

    public record IdentifyRequest(@NotBlank String mbid, Integer firstReleased) {
    }

    public record AlbumMetadata(Long albumId, String mbid, Integer year, Integer firstReleased, Integer effectiveYear,
                                MetadataSource source) {
        static AlbumMetadata of(Album a) {
            return new AlbumMetadata(a.getId(), a.getMbid(), a.getYear(), a.getFirstReleased(), a.effectiveYear(),
                    a.getMetadataSource());
        }
    }

    private final AlbumRepository albums;
    private final MusicBrainzClient musicBrainz;

    MetadataController(AlbumRepository albums, MusicBrainzClient musicBrainz) {
        this.albums = albums;
        this.musicBrainz = musicBrainz;
    }

    /** Candidatos ordenados pelo casador; sem contato configurado responde 409 e a UI desabilita o botão. */
    @GetMapping("/candidates")
    @Transactional(readOnly = true)
    Candidates candidates(@PathVariable Long id) {
        Album album = find(id);
        String artist = album.getArtist().getName();
        List<AlbumMatcher.Candidate> found = musicBrainz.searchReleaseGroups(artist, album.getTitle(), 10);
        List<CandidateView> ranked = AlbumMatcher.rank(album.getTitle(), artist, found).stream()
                .map(s -> new CandidateView(s.candidate().mbid(), s.candidate().title(), s.candidate().artist(),
                        s.candidate().primaryType(), s.candidate().secondaryTypes(), s.candidate().firstReleased(),
                        s.candidate().disambiguation(), round(s.score()), s.score() >= AlbumMatcher.CONFIDENT))
                .toList();
        return new Candidates(AlbumMatcher.cleanTitle(album.getTitle()), musicBrainz.configured(), ranked);
    }

    /** Grava a escolha do dono: MBID do release-group e ano da primeira edição (o que vale como era). */
    @PutMapping
    @Transactional
    AlbumMetadata identify(@PathVariable Long id, @Valid @RequestBody IdentifyRequest req) {
        Album album = find(id);
        if (album.getMetadataSource() == MetadataSource.MANUAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este álbum está marcado como MANUAL; edite-o à mão ou volte à origem antes de identificar.");
        }
        album.identify(req.mbid(), req.firstReleased());
        String artistMbid = musicBrainz.artistMbidOf(req.mbid());
        if (artistMbid != null && album.getArtist().getMbid() == null) {
            album.getArtist().setMbid(artistMbid);
        }
        return AlbumMetadata.of(album);
    }

    /** Desfaz a identificação: o ano volta a ser o da tag. */
    @DeleteMapping
    @Transactional
    AlbumMetadata forget(@PathVariable Long id) {
        Album album = find(id);
        album.forget();
        return AlbumMetadata.of(album);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private Album find(Long id) {
        return albums.findById(id).orElseThrow(() -> new NotFoundException("Album", id));
    }
}
