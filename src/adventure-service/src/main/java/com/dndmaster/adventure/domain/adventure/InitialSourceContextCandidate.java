package com.dndmaster.adventure.domain.adventure;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

public record InitialSourceContextCandidate(
        KnowledgeDocumentId knowledgeDocumentId,
        long extractionVersion,
        String locator,
        String excerpt,
        double score,
        String reason) {
    public InitialSourceContextCandidate {
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        if (extractionVersion <= 0) {
            throw new IllegalArgumentException("extraction version must be positive");
        }
        locator = required(locator, "locator");
        excerpt = required(excerpt, "excerpt");
        reason = required(reason, "reason");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
