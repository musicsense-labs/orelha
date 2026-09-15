package dev.musicsense.orelha.catalog;

public record ArtistResponse(Long id, String name, String country, Integer formedYear) {

    static ArtistResponse of(Artist a) {
        return new ArtistResponse(a.getId(), a.getName(), a.getCountry(), a.getFormedYear());
    }
}
