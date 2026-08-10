package com.dndmaster.ruleknowledge.domain.document.evidence;

import java.util.List;

/** One parser-neutral observation. It never asserts canonical ownership. */
public record StructuralEvidence(EvidenceKind kind, String targetId, String value,
                                 double confidence, List<String> provenance) {
    public StructuralEvidence {
        if (kind == null) throw new IllegalArgumentException("kind must not be null");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId must not be blank");
        value = value == null ? "" : value;
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}
