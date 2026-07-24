package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

// 검색이나 계획에 쓸 수 있는 단일 근거 조각이다.
public record RuntimeEvidence(
        RuntimeEvidenceType evidenceType,
        KnowledgeDocumentId knowledgeDocumentId,
        long extractionVersion,
        String locator,
        String excerpt) {
    public RuntimeEvidence {
        evidenceType = Objects.requireNonNull(evidenceType, "evidence type must not be null");
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        if (extractionVersion <= 0) {
            throw new IllegalArgumentException("extraction version must be positive");
        }
        locator = required(locator, "locator");
        excerpt = required(excerpt, "excerpt");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
