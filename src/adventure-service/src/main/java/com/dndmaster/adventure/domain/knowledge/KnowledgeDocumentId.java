package com.dndmaster.adventure.domain.knowledge;

import java.util.Objects;
import java.util.UUID;

public record KnowledgeDocumentId(UUID value) {
    public KnowledgeDocumentId {
        Objects.requireNonNull(value, "knowledge document id must not be null");
    }

    public static KnowledgeDocumentId generate() {
        return new KnowledgeDocumentId(UUID.randomUUID());
    }
}
