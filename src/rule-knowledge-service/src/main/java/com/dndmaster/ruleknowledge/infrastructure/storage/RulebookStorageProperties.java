package com.dndmaster.ruleknowledge.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rule-knowledge.storage")
public record RulebookStorageProperties(String root) {
    public RulebookStorageProperties {
        if (root == null || root.isBlank()) {
            throw new IllegalArgumentException("storage root must not be blank");
        }
    }

    public String resolveRoot() {
        return root;
    }
}
