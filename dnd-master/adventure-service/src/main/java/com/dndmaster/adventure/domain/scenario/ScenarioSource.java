package com.dndmaster.adventure.domain.scenario;

public record ScenarioSource(String storageKey, String originalFilename, String contentHash) {
    public ScenarioSource {
        storageKey = required(storageKey, "storage key");
        originalFilename = required(originalFilename, "original filename");
        contentHash = required(contentHash, "content hash");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
