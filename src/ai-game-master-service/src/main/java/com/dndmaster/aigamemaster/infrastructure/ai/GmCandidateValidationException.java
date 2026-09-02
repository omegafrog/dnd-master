package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.List;

public final class GmCandidateValidationException extends RuntimeException {
    private final List<GmCandidateViolation> violations;

    public GmCandidateValidationException(List<GmCandidateViolation> violations) {
        super(violations == null || violations.isEmpty() ? "GM candidate validation failed" : violations.get(0).safeMessage());
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<GmCandidateViolation> violations() { return violations; }
}
