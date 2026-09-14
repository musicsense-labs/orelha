package dev.rifflab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RiffLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiffLabApplication.class, args);
    }
}
