package com.dndmaster.ruleknowledge.domain.document.anchor;

import java.util.List;

/** Shadow-only, confirmed anchor ownership. Non-anchors deliberately stay absent. */
public record AnchorSkeleton(List<AnchorSkeletonNode> nodes, List<MatchedAnchor> unresolved) {
    public AnchorSkeleton {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
    }
}
