package com.dndmaster.ruleknowledge.domain.index;

import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public record IndexKey(RulebookId rulebookId, String contentHash, String embeddingModel, String indexVersion) {
    public IndexKey {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        contentHash = required(contentHash, "contentHash");
        embeddingModel = required(embeddingModel, "embeddingModel");
        indexVersion = required(indexVersion, "indexVersion");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
