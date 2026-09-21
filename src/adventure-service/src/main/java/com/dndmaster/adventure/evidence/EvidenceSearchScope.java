package com.dndmaster.adventure.evidence;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Server-verified session document scope supplied by the Adventure caller; adapters must not invent it. */
public record EvidenceSearchScope(UUID ownerId, UUID sessionId, UUID scenarioPackageId, String stageKey,
                                  String actionIntent, List<Document> documents, List<String> activeLocators) {
    public EvidenceSearchScope {
        Objects.requireNonNull(ownerId, "owner id must not be null");
        Objects.requireNonNull(sessionId, "session id must not be null");
        Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        stageKey = required(stageKey, "stage key");
        actionIntent = required(actionIntent, "action intent");
        documents = List.copyOf(Objects.requireNonNull(documents, "documents must not be null"));
        if (documents.isEmpty() || documents.stream().anyMatch(Objects::isNull)
                || documents.size() != new LinkedHashSet<>(documents).size()) {
            throw new IllegalArgumentException("documents must be nonempty and unique");
        }
        activeLocators = activeLocators == null ? List.of() : List.copyOf(activeLocators);
        if (activeLocators.stream().anyMatch(locator -> locator == null || locator.isBlank())
                || activeLocators.size() != new LinkedHashSet<>(activeLocators).size()) {
            throw new IllegalArgumentException("active locators must be unique and nonblank");
        }
    }
    public record Document(UUID id, long extractionVersion, String type) {
        public Document {
            Objects.requireNonNull(id, "document id must not be null");
            if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
            type = required(type, "document type").toUpperCase(java.util.Locale.ROOT);
            if (!type.equals("RULEBOOK") && !type.equals("STORYBOOK")) throw new IllegalArgumentException("document type is invalid");
        }
    }
    private static String required(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); return value.trim(); }
}
