package com.dndmaster.ruleknowledge.application.publication;

import java.util.Objects;

public record PublishedRagChunk(
        String processorChunkId,
        int sequence,
        String content,
        String embeddingText,
        String contentType,
        int tokenCount,
        String canonicalKey,
        SourceProvenance provenance,
        String parentKey) {
    public PublishedRagChunk(String processorChunkId, int sequence, String content, String embeddingText,
            SourceProvenance provenance, String parentKey) {
        this(processorChunkId, sequence, content, embeddingText, "unknown", content.strip().split("\\s+").length,
                processorChunkId, provenance, parentKey);
    }

    public PublishedRagChunk(
            String processorChunkId,
            int sequence,
            String content,
            String embeddingText,
            SourceProvenance provenance) {
        this(processorChunkId, sequence, content, embeddingText, provenance, null);
    }

    public PublishedRagChunk {
        if (processorChunkId == null || processorChunkId.isBlank()) throw new IllegalArgumentException("processor chunk id must not be blank");
        if (sequence < 0) throw new IllegalArgumentException("chunk sequence must not be negative");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("chunk content must not be blank");
        embeddingText = embeddingText == null || embeddingText.isBlank() ? content : embeddingText;
        if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("chunk content type must not be blank");
        if (tokenCount <= 0) throw new IllegalArgumentException("chunk token count must be positive");
        if (canonicalKey == null || canonicalKey.isBlank()) throw new IllegalArgumentException("chunk canonical key must not be blank");
        provenance = Objects.requireNonNull(provenance, "provenance must not be null");
        parentKey = parentKey == null || parentKey.isBlank() ? null : parentKey.trim();
    }
}
