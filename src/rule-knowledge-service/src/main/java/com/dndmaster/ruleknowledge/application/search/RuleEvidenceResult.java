package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.index.ChunkId;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.dndmaster.ruleknowledge.application.publication.SourceProvenance;
import java.util.List;
import java.util.Objects;

public record RuleEvidenceResult(
        RulebookId rulebookId,
        ChunkId chunkId,
        long extractionVersion,
        String locator,
        String excerpt,
        double score,
        String chapter,
        String section,
        SourceProvenance provenance) {

    public RuleEvidenceResult(RulebookId rulebookId, ChunkId chunkId, String locator, String excerpt,
            double score, String chapter, String section) {
        this(rulebookId, chunkId, 1, locator, excerpt, score, chapter, section,
                new SourceProvenance(1, section == null || section.isBlank() ? List.of() : List.of(section),
                        List.of(), null, locator));
    }

    public RuleEvidenceResult {
        Objects.requireNonNull(rulebookId, "rulebookId must not be null");
        Objects.requireNonNull(chunkId, "chunkId must not be null");
        if (extractionVersion <= 0) throw new IllegalArgumentException("extraction version must be positive");
        if (locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("locator must not be blank");
        }
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException("excerpt must not be blank");
        }
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
}
