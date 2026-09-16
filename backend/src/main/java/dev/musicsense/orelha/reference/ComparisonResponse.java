package dev.musicsense.orelha.reference;

import dev.musicsense.orelha.harmony.KeyMode;
import dev.musicsense.orelha.reference.ReferenceComparer.SectionMatch;

import java.util.List;

/**
 * Referência humana × nossa análise, para uma faixa: tonalidade (as duas, e se batem) e, por seção da
 * referência, a parte nossa mais parecida com similaridade de sequência e cobertura de vocabulário.
 * {@code sequenceSimilarity}/{@code vocabularyCoverage} no topo são as médias das seções.
 */
public record ComparisonResponse(Long trackId, Long runId, KeyView referenceKey, KeyView ourKey, boolean tonicMatches,
                                 boolean modeMatches, double sequenceSimilarity, double vocabularyCoverage,
                                 List<SectionMatch> sections, List<String> ourPartLabels) {

    public record KeyView(Integer tonicPc, KeyMode mode, String source) {
    }
}
