package com.dndmaster.ruleknowledge.infrastructure.persistence;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public record RuleSearchHit(
        RulebookId rulebookId,
        ChunkId chunkId,
        String locator,
        String content,
        double distance) {
    public RuleSearchHit {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
    }
}
