package com.dndmaster.ruleknowledge.application.publication;

import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.List;
import java.util.Objects;

public record RagExtractionPublicationRequest(
        RulebookId documentId,
        OwnerPlayerId ownerPlayerId,
        String operationId,
        String extractionVersion,
        String sourceHash,
        String policyVersion,
        String manifestHash,
        List<RagExtractionPage> pages,
        List<PublishedRagChunk> chunks,
        String embeddingModel) {
    public RagExtractionPublicationRequest(
            RulebookId documentId,
            OwnerPlayerId ownerPlayerId,
            String operationId,
            String extractionVersion,
            String sourceHash,
            String policyVersion,
            String manifestHash,
            List<RagExtractionPage> pages,
            List<PublishedRagChunk> chunks) {
        this(documentId, ownerPlayerId, operationId, extractionVersion, sourceHash, policyVersion,
                manifestHash, pages, chunks, "unspecified");
    }

    public RagExtractionPublicationRequest {
        Objects.requireNonNull(documentId, "document id must not be null");
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        requireText(operationId, "operation id"); requireText(extractionVersion, "extraction version");
        requireText(sourceHash, "source hash"); requireText(policyVersion, "policy version"); requireText(manifestHash, "manifest hash");
        pages = pages == null ? List.of() : List.copyOf(pages);
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        requireText(embeddingModel, "embedding model");
        if (pages.isEmpty()) throw new IllegalArgumentException("pages must not be empty");
        if (chunks.isEmpty()) throw new IllegalArgumentException("chunks must not be empty");
    }

    public RagExtractionPublicationRequest withEmbeddingModel(String model) {
        return new RagExtractionPublicationRequest(
                documentId, ownerPlayerId, operationId, extractionVersion, sourceHash,
                policyVersion, manifestHash, pages, chunks, model);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
