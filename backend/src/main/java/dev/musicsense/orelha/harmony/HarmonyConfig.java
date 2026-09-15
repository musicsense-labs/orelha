package dev.musicsense.orelha.harmony;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HarmonyConfig {

    @Bean
    PowerChordDetector powerChordDetector(
            @Value("${orelha.harmony.power-chord-third-ratio:0}") double thirdRatio) {
        return new PowerChordDetector(thirdRatio);
    }
}
