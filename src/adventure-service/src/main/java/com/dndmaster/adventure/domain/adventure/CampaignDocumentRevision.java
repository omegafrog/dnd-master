package com.dndmaster.adventure.domain.adventure;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import java.util.Objects;

public record CampaignDocumentRevision(
        KnowledgeDocumentId knowledgeDocumentId,
        long extractionVersion,
        String originalFilename) {
    public CampaignDocumentRevision {
        knowledgeDocumentId = Objects.requireNonNull(knowledgeDocumentId, "knowledge document id must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        originalFilename = required(originalFilename, "original filename");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
