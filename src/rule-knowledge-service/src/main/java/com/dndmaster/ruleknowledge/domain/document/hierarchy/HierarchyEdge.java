package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import java.util.List;

public record HierarchyEdge(String childId, String parentId, ResolutionStatus status, double confidence,
                            List<String> evidenceIds, String resolverVersion) {
    public HierarchyEdge {
        if (childId == null || childId.isBlank() || status == null || resolverVersion == null || resolverVersion.isBlank()) throw new IllegalArgumentException("invalid hierarchy edge");
        parentId = parentId == null ? "UNRESOLVED" : parentId;
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("invalid edge confidence");
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
