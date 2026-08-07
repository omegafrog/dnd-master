package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

/** Stable identity of a selected evidence fragment. Provider metadata is excluded. */
public record RuntimeEvidenceIdentity(
        RuntimeEvidenceType evidenceType,
        KnowledgeDocumentId knowledgeDocumentId,
        long extractionVersion,
        String locator) {
    public RuntimeEvidenceIdentity {
        evidenceType = Objects.requireNonNull(evidenceType, "evidence type must not be null");
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "document id must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (locator == null || locator.isBlank()) throw new IllegalArgumentException("locator must not be blank");
        locator = locator.trim();
    }
}
