package com.dndmaster.adventure.domain.adventure;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

public record ActiveSourceContext(
        KnowledgeDocumentId knowledgeDocumentId,
        long extractionVersion,
        String locator,
        String excerpt) {
    public ActiveSourceContext {
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        if (extractionVersion <= 0) {
            throw new IllegalArgumentException("extraction version must be positive");
        }
        locator = required(locator, "locator");
        excerpt = required(excerpt, "excerpt");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
