package dev.musicsense.orelha.analysis;

/** O que uma nota transcrita do stem de voz provavelmente é, à luz da letra (ver {@link VocalNoteClassifier}). */
public enum VocalNoteKind {
    /** Soa sob uma palavra transcrita: voz cantando texto. */
    LEXICAL,
    /** Dentro de um trecho que o ASR reconheceu como fala, mas sem palavra por cima: vocalise, "oh", melisma. */
    NON_LEXICAL,
    /** Fora de qualquer trecho de fala, ou num trecho que o ASR julgou não ser fala: provável vazamento de outro instrumento. */
    LIKELY_LEAK
}
