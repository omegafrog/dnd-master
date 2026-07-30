package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.util.Objects;

public record CharacterContextEvidence(
        KnowledgeDocumentId documentId, DocumentType documentType, long extractionVersion,
        String locator, String excerpt, double similarity) {
    public CharacterContextEvidence {
        Objects.requireNonNull(documentId, "documentId must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        if (extractionVersion < 0) throw new IllegalArgumentException("extractionVersion must not be negative");
        if (locator == null || locator.isBlank()) throw new IllegalArgumentException("locator must not be blank");
        if (excerpt == null || excerpt.isBlank()) throw new IllegalArgumentException("excerpt must not be blank");
        if (!Double.isFinite(similarity) || similarity < 0 || similarity > 1) {
            throw new IllegalArgumentException("similarity must be between zero and one");
        }
    }
}
