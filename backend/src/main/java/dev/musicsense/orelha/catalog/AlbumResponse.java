package dev.musicsense.orelha.catalog;

/**
 * {@code year} é o que as tags disseram (a edição no disco); {@code firstReleased} vem do MusicBrainz e
 * {@code effectiveYear} é o que vale como era — a UI mostra este.
 */
public record AlbumResponse(Long id, Long artistId, String title, Integer year, Integer firstReleased,
                            Integer effectiveYear, String mbid, MetadataSource metadataSource) {

    static AlbumResponse of(Album a) {
        return new AlbumResponse(a.getId(), a.getArtist().getId(), a.getTitle(), a.getYear(), a.getFirstReleased(),
                a.effectiveYear(), a.getMbid(), a.getMetadataSource());
    }
}
