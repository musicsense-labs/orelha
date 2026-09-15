package dev.musicsense.orelha.catalog;

import jakarta.validation.constraints.NotBlank;

public record ArtistRequest(@NotBlank String name, String country, Integer formedYear) {
}
