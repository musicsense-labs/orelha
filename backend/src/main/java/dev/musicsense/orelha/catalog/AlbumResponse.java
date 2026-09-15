package dev.musicsense.orelha.catalog;

public record AlbumResponse(Long id, Long artistId, String title, Integer year) {

    static AlbumResponse of(Album a) {
        return new AlbumResponse(a.getId(), a.getArtist().getId(), a.getTitle(), a.getYear());
    }
}
