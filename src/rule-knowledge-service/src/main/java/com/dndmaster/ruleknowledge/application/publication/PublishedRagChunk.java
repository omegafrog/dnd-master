package com.dndmaster.ruleknowledge.application.publication;

import java.util.Objects;

public record PublishedRagChunk(
        String processorChunkId,
        int sequence,
        String content,
        String embeddingText,
        SourceProvenance provenance) {
    public PublishedRagChunk {
        if (processorChunkId == null || processorChunkId.isBlank()) throw new IllegalArgumentException("processor chunk id must not be blank");
        if (sequence < 0) throw new IllegalArgumentException("chunk sequence must not be negative");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("chunk content must not be blank");
        embeddingText = embeddingText == null || embeddingText.isBlank() ? content : embeddingText;
        provenance = Objects.requireNonNull(provenance, "provenance must not be null");
    }
}
