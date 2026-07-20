package com.dndmaster.appall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.dndmaster")
@ConfigurationPropertiesScan(basePackages = "com.dndmaster")
public class DndMasterAllInOneApplication {
    public static void main(String[] args) {
        SpringApplication.run(DndMasterAllInOneApplication.class, args);
    }
}
