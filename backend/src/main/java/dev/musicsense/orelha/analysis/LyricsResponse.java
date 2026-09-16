package dev.musicsense.orelha.analysis;

import java.math.BigDecimal;
import java.util.List;

/**
 * Letra do run: trechos e palavras com tempo, cada um com o compasso em que começa (pela grade de beats
 * do run). {@code source} diz se é a transcrição do ASR ou a correção do dono; {@code language} é o idioma
 * detectado pelo ASR (ISO 639-1); null sem letra. Cada palavra traz o ataque da nota de voz que coincide
 * com ela ({@code noteStartS}, {@code midi}), quando há uma na folga configurada.
 */
public record LyricsResponse(Long runId, LyricSource source, String language, Float languageConfidence,
                             List<Segment> segments) {

    public record Segment(BigDecimal startS, BigDecimal endS, String text, Float noSpeechProb, Integer barNo,
                          List<Word> words) {
    }

    public record Word(BigDecimal startS, BigDecimal endS, String text, Float probability, Integer barNo,
                       BigDecimal noteStartS, Integer midi) {
    }
}
