package dev.musicsense.orelha.lyrics;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limiares da leitura da letra (orelha.lyrics.*): uma nota é "com texto" se sobrepõe uma palavra com folga
 * de {@code wordToleranceS} e probabilidade ≥ {@code wordMinProbability}. O {@code no_speech_prob} do ASR não
 * entra: medido no Creep, canto limpo já pontua ~0,8 em "não é fala" — o que separa é o Whisper devolver ou
 * não o trecho.
 */
@ConfigurationProperties("orelha.lyrics")
public record LyricsProperties(@DefaultValue("0.12") double wordToleranceS,
                               @DefaultValue("0.3") double wordMinProbability) {

    public VocalNoteClassifier classifier() {
        return new VocalNoteClassifier(wordToleranceS, wordMinProbability);
    }
}
