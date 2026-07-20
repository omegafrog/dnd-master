package com.dndmaster.aigamemaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiGameMasterServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiGameMasterServiceApplication.class, args);
    }
}
