package com.dndmaster.adventure.application.storyplan;

import java.util.List;
import java.util.Objects;

/** Typed adapter result for an invalid AI outline candidate, distinct from provider failure. */
public final class AdventureStoryPlanCandidateValidationException extends RuntimeException {
    private final List<String> violations;

    public AdventureStoryPlanCandidateValidationException(List<String> violations) {
        super(String.join("; ", Objects.requireNonNull(violations, "candidate violations must not be null")));
        if (violations.isEmpty()) throw new IllegalArgumentException("candidate violations must not be empty");
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
