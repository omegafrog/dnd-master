package com.dndmaster.ruleknowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RuleKnowledgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleKnowledgeServiceApplication.class, args);
    }
}
