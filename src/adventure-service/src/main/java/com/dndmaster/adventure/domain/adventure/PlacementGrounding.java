package com.dndmaster.adventure.domain.adventure;

import java.util.Objects;

/** Identifies whether tactical data is source-grounded or a bounded AI completion. */
public record PlacementGrounding(PlacementGroundingType type, String citation, String rationale) {
    public PlacementGrounding {
        type = Objects.requireNonNull(type, "grounding type must not be null");
        citation = citation == null ? "" : citation.trim();
        rationale = rationale == null ? "" : rationale.trim();
        if (type == PlacementGroundingType.SOURCE_CITATION && citation.isBlank()) {
            throw new IllegalArgumentException("source grounding requires a citation");
        }
        if (type == PlacementGroundingType.AI_INFERENCE && rationale.isBlank()) {
            throw new IllegalArgumentException("AI inference grounding requires a rationale");
        }
    }

    public static PlacementGrounding sourceCitation(String citation) {
        return new PlacementGrounding(PlacementGroundingType.SOURCE_CITATION, citation, "");
    }

    public static PlacementGrounding aiInference(String rationale) {
        return new PlacementGrounding(PlacementGroundingType.AI_INFERENCE, "", rationale);
    }
}
