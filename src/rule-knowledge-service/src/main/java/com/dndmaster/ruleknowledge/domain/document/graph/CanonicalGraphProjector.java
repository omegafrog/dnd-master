package com.dndmaster.ruleknowledge.domain.document.graph;

import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalDocumentTree;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.ResolutionStatus;
import java.util.List;

/** Projects canonical containment verbatim; it never infers hierarchy from document content. */
public final class CanonicalGraphProjector {
    public List<CanonicalContainmentEdge> project(CanonicalDocumentTree tree) {
        return tree.edges().stream()
                .filter(edge -> edge.status() == ResolutionStatus.CONFIRMED)
                .filter(edge -> !edge.parentId().isBlank() && !edge.parentId().equals("UNRESOLVED"))
                .map(edge -> new CanonicalContainmentEdge(edge.parentId(), edge.childId(), edge.resolverVersion()))
                .toList();
    }
}
