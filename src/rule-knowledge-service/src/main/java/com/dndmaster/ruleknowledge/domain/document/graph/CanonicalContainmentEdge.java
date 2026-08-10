package com.dndmaster.ruleknowledge.domain.document.graph;

/** Read-model edge; it is emitted only from a confirmed canonical decision. */
public record CanonicalContainmentEdge(String parentId, String childId, String resolverVersion) {
    public CanonicalContainmentEdge {
        if (parentId == null || parentId.isBlank() || childId == null || childId.isBlank()
                || resolverVersion == null || resolverVersion.isBlank()) {
            throw new IllegalArgumentException("canonical containment edge requires identifiers");
        }
    }
}
