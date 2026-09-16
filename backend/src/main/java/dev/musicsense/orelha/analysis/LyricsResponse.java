package dev.musicsense.orelha.analysis;

import java.math.BigDecimal;
import java.util.List;

/**
 * Letra do run canônico: trechos e palavras com tempo, cada um com o compasso em que começa (pela grade
 * de beats do run). {@code language} é o idioma detectado pelo ASR (ISO 639-1); null sem letra.
 */
public record LyricsResponse(Long runId, String language, Float languageConfidence, List<Segment> segments) {

    public record Segment(BigDecimal startS, BigDecimal endS, String text, Float noSpeechProb, Integer barNo,
                          List<Word> words) {
    }

    public record Word(BigDecimal startS, BigDecimal endS, String text, Float probability, Integer barNo) {
    }
}
