package dev.rifflab.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlbumRequest(@NotNull Long artistId, @NotBlank String title, Integer year) {
}
