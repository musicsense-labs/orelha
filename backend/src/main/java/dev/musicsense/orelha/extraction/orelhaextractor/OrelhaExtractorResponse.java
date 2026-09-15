package dev.musicsense.orelha.extraction.orelhaextractor;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Espelho do JSON do orelha-extractor (extractor/README.md). Só aqui se conhece esse formato. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
record OrelhaExtractorResponse(Extractor extractor, Audio audio, Key key, Tempo tempo, List<Beat> beats,
                             List<ChordSegment> chords, List<BassNote> bassNotes, List<Timbre> timbre,
                             String featuresPath, Map<String, String> stems) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Extractor(String name, String version, Map<String, String> models) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Audio(BigDecimal durationS, Integer sampleRate, BigDecimal integratedLufs) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Key(int tonicPc, String mode, Float confidence) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Tempo(BigDecimal bpm, String timeSignature) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Beat(BigDecimal timeS, int position) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record ChordSegment(BigDecimal startS, BigDecimal endS, String label, float[] chroma, float[] chromaLow) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BassNote(BigDecimal startS, BigDecimal endS, int midi, Integer velocity) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Timbre(String stemModel, String stem, Float centroidMean, Float centroidStd, Float flatnessMean,
                  Float rolloffP95, Float rmsMean) {
    }
}
