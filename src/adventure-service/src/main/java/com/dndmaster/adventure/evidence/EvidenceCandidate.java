package com.dndmaster.adventure.evidence;

import java.util.Objects;
import java.util.UUID;

/** Immutable candidate supplied by the session-scoped Document Knowledge boundary. */
public record EvidenceCandidate(UUID id, String documentId, String documentType, String locator, String excerpt) {
    public EvidenceCandidate {
        Objects.requireNonNull(id, "id must not be null");
        documentId = required(documentId, "document id");
        documentType = required(documentType, "document type");
        locator = required(locator, "locator");
        excerpt = required(excerpt, "excerpt");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
