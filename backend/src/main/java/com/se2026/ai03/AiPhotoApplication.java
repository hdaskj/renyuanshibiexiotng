package com.se2026.ai03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiPhotoApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiPhotoApplication.class, args);
    }
}
