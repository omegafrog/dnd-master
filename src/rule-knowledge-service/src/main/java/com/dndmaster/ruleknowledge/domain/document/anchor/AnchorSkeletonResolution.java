package com.dndmaster.ruleknowledge.domain.document.anchor;

import java.util.List;

/** Shadow publication payload: matched anchors plus a validated, partial skeleton. */
public record AnchorSkeletonResolution(List<StructuralAnchor> anchors, List<MatchedAnchor> matches,
                                       AnchorSkeleton skeleton, List<String> diagnostics) {
    public AnchorSkeletonResolution {
        anchors = anchors == null ? List.of() : List.copyOf(anchors);
        matches = matches == null ? List.of() : List.copyOf(matches);
        if (skeleton == null) throw new IllegalArgumentException("skeleton must not be null");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
