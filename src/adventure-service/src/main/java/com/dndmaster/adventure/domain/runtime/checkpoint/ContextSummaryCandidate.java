package com.dndmaster.adventure.domain.runtime.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ContextSummaryCandidate(String summary, List<String> unresolvedThreats, UUID planRevisionId, long planVersion) {
    public ContextSummaryCandidate {
        if (summary == null || summary.isBlank()) throw new IllegalArgumentException("summary must not be blank");
        unresolvedThreats = List.copyOf(Objects.requireNonNull(unresolvedThreats));
        planRevisionId = Objects.requireNonNull(planRevisionId);
        if (planVersion < 1) throw new IllegalArgumentException("plan version must be positive");
    }
}
