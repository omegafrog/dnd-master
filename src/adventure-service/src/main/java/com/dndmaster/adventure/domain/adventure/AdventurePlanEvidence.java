package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;
import java.util.UUID;

public record AdventurePlanEvidence(String documentType, UUID documentId, long extractionVersion,
        String locator, String quote, double confidence) {
    public AdventurePlanEvidence {
        documentType = required(documentType, "document type");
        documentId = Objects.requireNonNull(documentId, "document id must not be null");
        locator = required(locator, "evidence locator");
        quote = required(quote, "evidence quote");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be between 0 and 1");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
