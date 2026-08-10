package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;

/** Canonical relation is separate from parser relation; leaf span is immutable input provenance. */
public record CanonicalNode(String id, String parserParentId, Integer parserLevel, NormalizedSourceSpan leafSpan, boolean synthetic) {
    public CanonicalNode {
        if (id == null || id.isBlank() || !synthetic && leafSpan == null) throw new IllegalArgumentException("canonical node requires id and leaf span");
        parserParentId = parserParentId == null ? "" : parserParentId;
    }
}
