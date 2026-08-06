package com.dndmaster.adventure.domain.scenario;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

public record MapSourceReference(KnowledgeDocumentId knowledgeDocumentId, long extractionVersion, String locator) {
    public MapSourceReference {
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (locator == null || locator.isBlank()) throw new IllegalArgumentException("locator must not be blank");
        locator = locator.trim();
    }
}
