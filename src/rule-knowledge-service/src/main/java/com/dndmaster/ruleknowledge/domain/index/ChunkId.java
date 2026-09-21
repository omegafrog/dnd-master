package com.dndmaster.ruleknowledge.domain.index;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;
import java.util.UUID;

public record ChunkId(UUID value) {
    public ChunkId { Objects.requireNonNull(value, "chunk id must not be null"); }

    public static ChunkId fromStableValue(String value) {
        Objects.requireNonNull(value, "chunk id value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("chunk id value must not be blank");
        return new ChunkId(UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** Stable publication identity includes the document and extraction that produced a processor chunk. */
    public static ChunkId fromPublicationValue(RulebookId documentId, String extractionVersion, String processorChunkId) {
        Objects.requireNonNull(documentId, "document id must not be null");
        Objects.requireNonNull(extractionVersion, "extraction version must not be null");
        Objects.requireNonNull(processorChunkId, "processor chunk id must not be null");
        if (extractionVersion.isBlank()) throw new IllegalArgumentException("extraction version must not be blank");
        return fromStableValue(documentId.value() + ":" + extractionVersion.length() + ":" + extractionVersion
                + ":" + processorChunkId.length() + ":" + processorChunkId);
    }
}
