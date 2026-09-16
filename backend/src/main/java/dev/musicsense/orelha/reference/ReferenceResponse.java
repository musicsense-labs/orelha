package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.harmony.KeyMode;
import dev.musicsense.orelha.reference.ReferenceAnalysis.Section;

import java.time.Instant;
import java.util.List;

/** A análise de referência guardada para a faixa (null em tudo quando não há). */
public record ReferenceResponse(Long trackId, String source, String url, Integer tonicPc, KeyMode mode, String rawText,
                                List<Section> sections, Instant updatedAt) {
}
