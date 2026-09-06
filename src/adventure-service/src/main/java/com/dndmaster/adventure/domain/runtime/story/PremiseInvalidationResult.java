package com.dndmaster.adventure.domain.runtime.story;

import java.util.Objects;

public record PremiseInvalidationResult(boolean accepted, PremiseInvalidationAudit audit) {
    public PremiseInvalidationResult {
        audit = Objects.requireNonNull(audit, "invalidation audit must not be null");
        if (accepted != audit.accepted()) throw new IllegalArgumentException("decision and audit disagree");
    }
}
