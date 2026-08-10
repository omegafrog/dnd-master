package com.dndmaster.ruleknowledge.domain.document.normalized;

import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;

public record NormalizedSourceSpan(String sourceId, int page, int order, Integer start, Integer end,
                                   BoundingBox boundingBox) {
    public NormalizedSourceSpan {
        if (sourceId == null || sourceId.isBlank()) throw new IllegalArgumentException("sourceId must not be blank");
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        if (order < 0) throw new IllegalArgumentException("order must not be negative");
        if (start != null && start < 0 || end != null && end < 0) {
            throw new IllegalArgumentException("source offsets must not be negative");
        }
        if (start != null && end != null && end < start) throw new IllegalArgumentException("end must not precede start");
    }
}
