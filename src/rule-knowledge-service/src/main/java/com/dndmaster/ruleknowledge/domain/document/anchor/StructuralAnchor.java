package com.dndmaster.ruleknowledge.domain.document.anchor;

import java.util.List;

/** A source-addressable anchor candidate; never a canonical parent decision. */
public record StructuralAnchor(String id, String title, String locator, String numbering,
                               Integer levelSuggestion, List<String> evidenceIds, double confidence) {
    public StructuralAnchor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        title = title == null ? "" : title.trim();
        locator = locator == null ? "" : locator.trim();
        numbering = numbering == null ? "" : numbering.trim();
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
    }
}
