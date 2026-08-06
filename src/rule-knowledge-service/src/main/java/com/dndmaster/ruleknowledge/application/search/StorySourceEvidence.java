package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.KnowledgeDocumentId;
import java.util.Objects;

public record StorySourceEvidence(
        KnowledgeDocumentId documentId,
        long extractionVersion,
        String sourceSpanLocator,
        String excerpt,
        double score,
        String visibility,
        String disclosureEvent,
        Long disclosureTurn) {
    public StorySourceEvidence(KnowledgeDocumentId documentId, long extractionVersion, String sourceSpanLocator,
            String excerpt, double score) {
        this(documentId, extractionVersion, sourceSpanLocator, excerpt, score, "GM_ONLY", null, 0L);
    }
    public StorySourceEvidence {
        Objects.requireNonNull(documentId, "document id must not be null");
        if (extractionVersion < 0) {
            throw new IllegalArgumentException("extraction version must not be negative");
        }
        if (sourceSpanLocator == null || sourceSpanLocator.isBlank()) {
            throw new IllegalArgumentException("source span locator must not be blank");
        }
        if (excerpt == null || excerpt.isBlank()) {
            throw new IllegalArgumentException("excerpt must not be blank");
        }
        if (!Double.isFinite(score) || score < 0d) {
            throw new IllegalArgumentException("score must be finite and non-negative");
        }
        if (visibility == null || visibility.isBlank()
                || !java.util.Set.of("PLAYER_VISIBLE", "GM_ONLY", "NPC_PRIVATE", "REVEALED_AFTER_EVENT", "DISCOVERED", "PUBLIC_SUMMARY").contains(visibility)) {
            throw new IllegalArgumentException("unsupported visibility");
        }
        if (disclosureTurn == null || disclosureTurn < 0) throw new IllegalArgumentException("disclosure turn must be non-negative");
    }
}
