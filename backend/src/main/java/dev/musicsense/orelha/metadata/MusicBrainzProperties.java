package dev.musicsense.orelha.metadata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A API do MusicBrainz é aberta para leitura, mas exige {@code User-Agent} com contato e limita a uma
 * requisição por segundo por IP — os dois são condição de uso, não detalhe. Sem contato preenchido o
 * cliente não sai do lugar (melhor falhar aqui do que ser bloqueado lá).
 */
@ConfigurationProperties(prefix = "orelha.musicbrainz")
public record MusicBrainzProperties(String url, String contact, String appVersion) {

    public MusicBrainzProperties {
        url = url == null || url.isBlank() ? "https://musicbrainz.org/ws/2" : url;
        appVersion = appVersion == null || appVersion.isBlank() ? "0.7.0" : appVersion;
    }

    public boolean configured() {
        return contact != null && !contact.isBlank();
    }

    /** No formato que eles pedem: {@code Orelha/0.7.0 ( contato )}. */
    public String userAgent() {
        return "Orelha/" + appVersion + " ( " + contact + " )";
    }
}
