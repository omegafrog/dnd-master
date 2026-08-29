package com.dndmaster.gmeval.registry;

import java.time.Instant;
import java.util.List;

/** Human decision over representative outputs. */
public record ReviewerDecision(String reviewerId, boolean approved, String reason,
                               List<String> representativeSampleIds, Instant reviewedAt) {
    public ReviewerDecision {
        if (reviewerId == null || reviewerId.isBlank()) throw new IllegalArgumentException("reviewer id required");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("review reason required");
        representativeSampleIds = List.copyOf(representativeSampleIds == null ? List.of() : representativeSampleIds);
        reviewedAt = reviewedAt == null ? Instant.now() : reviewedAt;
    }
}
