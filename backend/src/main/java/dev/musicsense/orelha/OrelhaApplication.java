package dev.musicsense.orelha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OrelhaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrelhaApplication.class, args);
    }
}
