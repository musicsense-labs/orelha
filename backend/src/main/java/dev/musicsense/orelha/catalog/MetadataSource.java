package dev.musicsense.orelha.catalog;

/** De onde vêm os metadados do álbum, na mesma ordem de preferência das outras fontes do projeto. */
public enum MetadataSource {
    /** O que o arquivo trazia (ID3/Vorbis) ou o nome da pasta. */
    TAGS,
    /** Identificado no MusicBrainz e aceito pelo dono. */
    MUSICBRAINZ,
    /** Digitado à mão; nada externo sobrescreve. */
    MANUAL
}
