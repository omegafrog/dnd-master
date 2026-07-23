package com.dndmaster.adventure.domain.scenario;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

public record ScenarioBundleDocumentSelection(
        KnowledgeDocumentId knowledgeDocumentId,
        ScenarioBundleDocumentRole role,
        KnowledgeDocumentStatus status,
        String originalFilename,
        String documentType,
        long extractionVersion) {
    public ScenarioBundleDocumentSelection {
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        role = Objects.requireNonNull(role, "role must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        originalFilename = Objects.requireNonNull(originalFilename, "original filename must not be null");
        documentType = Objects.requireNonNull(documentType, "document type must not be null");
        if (extractionVersion <= 0) {
            throw new IllegalArgumentException("extraction version must be positive");
        }
    }
}
