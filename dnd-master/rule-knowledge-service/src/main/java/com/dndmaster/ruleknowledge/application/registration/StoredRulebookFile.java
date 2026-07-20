package com.dndmaster.ruleknowledge.application.registration;

import java.util.Objects;

public record StoredRulebookFile(String key) {
    public StoredRulebookFile {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("stored file key must not be blank");
        }
        key = key.trim();
    }
}
