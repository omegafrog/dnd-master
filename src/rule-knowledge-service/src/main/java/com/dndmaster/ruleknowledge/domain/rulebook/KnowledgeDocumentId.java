package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;
import java.util.UUID;

public record KnowledgeDocumentId(UUID value) {
    public KnowledgeDocumentId {
        Objects.requireNonNull(value, "knowledge document id must not be null");
    }

    public static KnowledgeDocumentId generate() {
        return new KnowledgeDocumentId(UUID.randomUUID());
    }

    public static KnowledgeDocumentId fromRulebookId(RulebookId legacyId) {
        return new KnowledgeDocumentId(Objects.requireNonNull(legacyId, "legacy rulebook id must not be null").value());
    }

    public RulebookId asRulebookId() {
        return new RulebookId(value);
    }
}
