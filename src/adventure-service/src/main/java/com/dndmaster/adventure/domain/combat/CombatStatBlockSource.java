package com.dndmaster.adventure.domain.combat;

import java.util.UUID;

/** Immutable provenance for a materialized enemy stat block. */
public record CombatStatBlockSource(UUID knowledgeDocumentId, long extractionVersion, String locator) {
    public CombatStatBlockSource {
        if (knowledgeDocumentId == null || extractionVersion <= 0 || locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("combat stat block source is incomplete");
        }
        locator = locator.trim();
    }
}
