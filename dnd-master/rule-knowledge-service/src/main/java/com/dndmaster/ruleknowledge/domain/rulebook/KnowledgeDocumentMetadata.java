package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;

public record KnowledgeDocumentMetadata(
        KnowledgeDocumentId id,
        OwnerPlayerId ownerPlayerId,
        DocumentType documentType,
        String originalFilename,
        RulebookFormat format,
        long fileSize) {
    public KnowledgeDocumentMetadata {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        documentType = DocumentType.require(documentType);
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("original filename must not be blank");
        }
        originalFilename = originalFilename.trim();
        Objects.requireNonNull(format, "format must not be null");
        if (fileSize <= 0) throw new IllegalArgumentException("fileSize must be positive");
    }
}
