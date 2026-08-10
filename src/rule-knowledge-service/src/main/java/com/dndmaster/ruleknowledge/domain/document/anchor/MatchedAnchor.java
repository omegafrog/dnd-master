package com.dndmaster.ruleknowledge.domain.document.anchor;

import java.util.List;

public record MatchedAnchor(StructuralAnchor anchor, String bodyElementId, double score,
                            boolean confirmed, List<String> scoreEvidence) {
    public MatchedAnchor {
        if (anchor == null) throw new IllegalArgumentException("anchor must not be null");
        bodyElementId = bodyElementId == null ? "" : bodyElementId;
        if (!Double.isFinite(score) || score < 0 || score > 1) throw new IllegalArgumentException("score must be finite and between 0 and 1");
        scoreEvidence = scoreEvidence == null ? List.of() : List.copyOf(scoreEvidence);
        if (confirmed && bodyElementId.isBlank()) throw new IllegalArgumentException("confirmed match requires body element");
    }

    public static MatchedAnchor unresolved(StructuralAnchor anchor, List<String> evidence) {
        return new MatchedAnchor(anchor, "", 0, false, evidence);
    }
}
