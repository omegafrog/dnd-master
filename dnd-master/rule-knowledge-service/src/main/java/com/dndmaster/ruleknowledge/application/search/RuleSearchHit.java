package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public record RuleSearchHit(
        KnowledgeDocumentId knowledgeDocumentId,
        DocumentType documentType,
        ChunkId chunkId,
        String locator,
        String content,
        double distance,
        String chapter,
        String section) {
    public RuleSearchHit {
        Objects.requireNonNull(knowledgeDocumentId, "knowledgeDocumentId must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
    }

    public RulebookId rulebookId() {
        return new RulebookId(knowledgeDocumentId.value());
    }
}
