package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import java.util.Objects;

public record RuleEvidenceResult(
        RulebookId rulebookId,
        ChunkId chunkId,
        String locator,
        String excerpt,
        double score) {

    public RuleEvidenceResult {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException("excerpt must not be blank");
        }
    }
}
