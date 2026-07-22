package com.dndmaster.adventure.domain.inquiry;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

public record SourceLocation(KnowledgeDocumentId knowledgeDocumentId, String locator) {
    public SourceLocation {
        Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        if (locator == null || locator.isBlank()) throw new IllegalArgumentException("source locator must not be blank");
        locator = locator.trim();
    }

    public RulebookId rulebookId() {
        return new RulebookId(knowledgeDocumentId.value());
    }
}
