package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public record RuleEvidenceResult(
        KnowledgeDocumentId knowledgeDocumentId,
        DocumentType documentType,
        ChunkId chunkId,
        String locator,
        String excerpt,
        double score,
        String chapter,
        String section) {

    public RuleEvidenceResult {
        Objects.requireNonNull(knowledgeDocumentId, "knowledgeDocumentId must not be null");
        Objects.requireNonNull(documentType, "documentType must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException("excerpt must not be blank");
        }
    }

    public RulebookId rulebookId() {
        return new RulebookId(knowledgeDocumentId.value());
    }
}
