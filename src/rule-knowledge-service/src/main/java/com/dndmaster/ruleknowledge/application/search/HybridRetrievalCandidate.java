package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.*;

public record HybridRetrievalCandidate(
        UUID ownerId, KnowledgeDocumentId documentId, DocumentType documentType, long extractionVersion,
        String locator, String excerpt, double denseScore, double keywordScore, UUID chunkId,
        String sessionId, String packageId, String stage, String visibility, double score) {
    public HybridRetrievalCandidate(UUID ownerId, KnowledgeDocumentId documentId, DocumentType documentType,
            long extractionVersion, String locator, String excerpt, double denseScore, double keywordScore,
            UUID chunkId, String sessionId, String packageId, String stage, String visibility) {
        this(ownerId, documentId, documentType, extractionVersion, locator, excerpt, denseScore, keywordScore,
                chunkId, sessionId, packageId, stage, visibility, 0d);
    }
    public HybridRetrievalCandidate {
        Objects.requireNonNull(ownerId); Objects.requireNonNull(documentId); Objects.requireNonNull(documentType);
        Objects.requireNonNull(locator); Objects.requireNonNull(excerpt); Objects.requireNonNull(chunkId);
        Objects.requireNonNull(sessionId); Objects.requireNonNull(packageId); Objects.requireNonNull(stage); Objects.requireNonNull(visibility);
        if (locator.isBlank() || excerpt.isBlank() || extractionVersion < 0 || !Double.isFinite(denseScore)
                || !Double.isFinite(keywordScore) || !Double.isFinite(score)) throw new IllegalArgumentException("invalid retrieval candidate");
    }
    public HybridRetrievalCandidate withScore(double value) {
        return new HybridRetrievalCandidate(ownerId, documentId, documentType, extractionVersion, locator, excerpt,
                denseScore, keywordScore, chunkId, sessionId, packageId, stage, visibility, value);
    }
    public String key() { return documentId.value() + ":" + extractionVersion + ":" + locator; }
}
