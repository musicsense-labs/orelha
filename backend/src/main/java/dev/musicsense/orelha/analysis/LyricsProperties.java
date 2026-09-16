package dev.musicsense.orelha.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limiares da leitura da letra (orelha.lyrics.*): acima de {@code noSpeechThreshold} um trecho não conta
 * como fala — desligado por padrão (1.0), porque canto limpo já pontua ~0,8 em "não é fala" e o Whisper
 * só devolve trechos que ele mesmo aceitou como fala; uma nota é "com texto" se sobrepõe uma palavra com folga de {@code wordToleranceS} e
 * probabilidade ≥ {@code wordMinProbability}.
 */
@ConfigurationProperties("orelha.lyrics")
public record LyricsProperties(@DefaultValue("1.0") double noSpeechThreshold,
                               @DefaultValue("0.12") double wordToleranceS,
                               @DefaultValue("0.3") double wordMinProbability) {

    public VocalNoteClassifier classifier() {
        return new VocalNoteClassifier(noSpeechThreshold, wordToleranceS, wordMinProbability);
    }
}
