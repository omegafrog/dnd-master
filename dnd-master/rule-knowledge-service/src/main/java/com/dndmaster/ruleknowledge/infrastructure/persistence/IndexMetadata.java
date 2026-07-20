package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.domain.index.IndexId;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public record IndexMetadata(
        IndexId indexId,
        RulebookId rulebookId,
        OwnerPlayerId ownerPlayerId,
        String embeddingModel,
        int dimension,
        String indexVersion) {
    public IndexMetadata {
        Objects.requireNonNull(indexId, "indexId must not be null");
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(ownerPlayerId, "ownerPlayerId must not be null");
        embeddingModel = required(embeddingModel, "embeddingModel");
        indexVersion = required(indexVersion, "indexVersion");
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
