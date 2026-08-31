package com.dndmaster.adventure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
        "com.dndmaster.adventure",
        "com.dndmaster.diceroll",
        "com.dndmaster.combatmap"
})
public class AdventureServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdventureServiceApplication.class, args);
    }
}
