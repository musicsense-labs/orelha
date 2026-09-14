package dev.rifflab.catalog;

import java.util.List;

/** Resultado de uma importação em lote: o que entrou, o que foi pulado e por quê. */
public record ImportReport(List<Imported> imported, List<Skipped> skipped) {

    public record Imported(Long trackId, String file, String artist, String album, String title, Integer trackNo,
                           boolean fromTags) {
    }

    public record Skipped(String file, String reason) {
    }
}
