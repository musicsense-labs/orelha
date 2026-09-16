package dev.musicsense.orelha.extraction;

import dev.musicsense.orelha.harmony.Chord;
import dev.musicsense.orelha.harmony.KeyMode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Tudo que uma extração devolve, já traduzido para o vocabulário do domínio. Só dado bruto.
 * stems = caminhos (no volume do extrator) dos stems persistidos, por nome; vazio se não há.
 */
public record ExtractionResult(Provenance provenance, AudioInfo audio, KeyEstimate key, Tempo tempo,
                               List<BeatEvent> beats, List<ChordEvent> chords, List<NoteEvent> bassNotes,
                               List<NoteEvent> vocalNotes, Lyrics lyrics, List<TimbreStat> timbre,
                               String featuresPath, Map<String, String> stems) {

    public record Provenance(String name, String version, Map<String, String> models) {
    }

    public record AudioInfo(BigDecimal durationS, Integer sampleRate, BigDecimal integratedLufs) {
    }

    public record KeyEstimate(int tonicPc, KeyMode mode, Float confidence) {
    }

    public record Tempo(BigDecimal bpm, String timeSignature) {
    }

    /** position = posição no compasso, 1 = downbeat. */
    public record BeatEvent(BigDecimal timeS, int position) {
    }

    /**
     * chroma = 12 energias por classe de altura (C = 0) na mixagem; chromaLow = idem no stem de guitarra,
     * registro C2–C4 (evidência de power chord). null quando o extrator não fornece.
     */
    public record ChordEvent(BigDecimal startS, BigDecimal endS, Chord chord, float[] chroma, float[] chromaLow,
                             Float confidence) {
    }

    /** Nota MIDI transcrita de um stem (baixo ou voz). */
    public record NoteEvent(BigDecimal startS, BigDecimal endS, int midi, Integer velocity) {
    }

    /**
     * Letra por ASR sobre o stem de voz (extrator ≥ 0.6.0): trechos com a probabilidade de "não é fala" e
     * palavras com tempo. Vazio (não null) quando o extrator não transcreve.
     */
    public record Lyrics(String language, Float languageConfidence, List<LyricSegmentEvent> segments) {
        public static final Lyrics NONE = new Lyrics(null, null, List.of());
    }

    public record LyricSegmentEvent(BigDecimal startS, BigDecimal endS, String text, Float noSpeechProb,
                                    List<LyricWordEvent> words) {
    }

    public record LyricWordEvent(BigDecimal startS, BigDecimal endS, String text, Float probability) {
    }

    public record TimbreStat(String stemModel, String stem, Float centroidMean, Float centroidStd,
                             Float flatnessMean, Float rolloffP95, Float rmsMean) {
    }
}
