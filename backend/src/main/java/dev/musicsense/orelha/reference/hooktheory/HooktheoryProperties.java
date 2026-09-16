package dev.musicsense.orelha.reference.hooktheory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * orelha.hooktheory.* — a API Trends do Hooktheory exige o token da conta do dono ({@code activkey},
 * obtido por ele com POST /v1/users/auth). Sem token, os endpoints respondem 409 e a UI explica.
 * Configure por variável de ambiente ORELHA_HOOKTHEORY_ACTIVKEY; nunca no repositório.
 */
@ConfigurationProperties("orelha.hooktheory")
public record HooktheoryProperties(@DefaultValue("https://api.hooktheory.com/v1/") String url, String activkey) {

    public boolean configured() {
        return activkey != null && !activkey.isBlank();
    }
}
