package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import java.util.List;
import java.util.Objects;

public record RuleSearchHit(
        RulebookId rulebookId,
        ChunkId chunkId,
        long extractionVersion,
        String locator,
        String content,
        double distance,
        String chapter,
        String section,
        SourceProvenance provenance) {
    public RuleSearchHit(RulebookId rulebookId, ChunkId chunkId, String locator, String content,
            double distance, String chapter, String section) {
        this(rulebookId, chunkId, 1, locator, content, distance, chapter, section,
                new SourceProvenance(1, section == null || section.isBlank() ? List.of() : List.of(section),
                        List.of(), null, locator));
    }

    public RuleSearchHit {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        if (provenance == null) throw new IllegalArgumentException("provenance must not be null");
    }
}
