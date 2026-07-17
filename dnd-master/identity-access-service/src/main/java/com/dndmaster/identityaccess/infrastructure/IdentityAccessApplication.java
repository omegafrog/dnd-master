package com.dndmaster.identityaccess.infrastructure;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.dndmaster.identityaccess")
public class IdentityAccessApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdentityAccessApplication.class, args);
    }
}
