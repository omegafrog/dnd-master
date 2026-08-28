package com.dndmaster.adventure.application.storyplan;

import java.util.List;

/** Named application policy boundary for dependency-aware projection repair. */
public final class ProjectionDependencyPolicy {
    private ProjectionDependencyPolicy() { }

    public static RepairScope scope(String fullSerializedCandidate,
            List<AdventureStoryPlanProjectionViolation> violations) {
        return AdventureStoryPlanProjectionDependencyPolicy.scope(fullSerializedCandidate, violations);
    }
}
