package dev.rifflab.harmony;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HarmonyConfig {

    @Bean
    PowerChordDetector powerChordDetector(
            @Value("${rifflab.harmony.power-chord-third-ratio:0}") double thirdRatio) {
        return new PowerChordDetector(thirdRatio);
    }
}
