package com.dndmaster.adventure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdventureServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdventureServiceApplication.class, args);
    }
}
