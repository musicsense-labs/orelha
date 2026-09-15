package dev.musicsense.orelha.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** audioPath é um caminho local legível pelo backend; o SHA-256 é calculado no cadastro. */
public record TrackRequest(@NotNull Long albumId, @NotBlank String title, Integer trackNo, @NotBlank String audioPath) {
}
