package dev.musicsense.orelha.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Resultado de uma importação em lote: o que entrou, o que foi pulado e por quê. */
public record ImportReport(List<Imported> imported, List<Skipped> skipped) {

    public record Imported(Long trackId, String file, String artist, String album, String title, Integer trackNo,
                           boolean fromTags) {
    }

    public record Skipped(String file, String reason) {
    }

    /**
     * Uma linha da pré-visualização, editável na UI. {@code key} identifica o arquivo de origem
     * (caminho no servidor ou no staging) e volta intacto na confirmação.
     */
    public record Item(@NotBlank String key, String file, @NotBlank String artist, @NotBlank String album, Integer year,
                       @NotBlank String title, Integer trackNo, boolean fromTags, boolean duplicate) {
    }

    /** {@code stagingId} é null quando os arquivos estão no disco do servidor (ficam no lugar). */
    public record Preview(String stagingId, List<Item> items) {
    }

    public record Confirmation(String stagingId, @NotNull List<Item> items) {
    }
}
