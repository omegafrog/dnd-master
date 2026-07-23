package com.dndmaster.adventure.domain.scenario;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

public record ScenarioSourceReference(
        KnowledgeDocumentId knowledgeDocumentId, long extractionVersion, String locator) {
    public ScenarioSourceReference {
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        locator = Objects.requireNonNull(locator, "locator must not be null");
        if (extractionVersion <= 0 || locator.isBlank()) {
            throw new IllegalArgumentException("source reference must identify a positive extraction and locator");
        }
    }
}
