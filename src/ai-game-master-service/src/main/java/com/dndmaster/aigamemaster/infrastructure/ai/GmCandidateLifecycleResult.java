package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.Objects;

/** Result of the initial candidate and at most one bounded repair attempt. */
public record GmCandidateLifecycleResult<T>(GmCompletionResult<T> completion, int attemptCount) {
    public GmCandidateLifecycleResult {
        completion = Objects.requireNonNull(completion, "completion required");
        if (attemptCount < 1 || attemptCount > 2) throw new IllegalArgumentException("GM candidate attempts must be one or two");
    }
}
