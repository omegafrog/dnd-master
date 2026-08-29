package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public record VerificationResult(VerificationStatus status, List<VerificationViolation> violations, int rewriteCount) {
    public VerificationResult {
        status = Objects.requireNonNull(status);
        violations = List.copyOf(Objects.requireNonNull(violations));
        if (rewriteCount < 0 || rewriteCount > 1) throw new IllegalArgumentException("rewrite count must be 0 or 1");
    }

    public static VerificationResult pass() { return new VerificationResult(VerificationStatus.PASS, List.of(), 0); }
    public boolean hasErrors() { return violations.stream().anyMatch(v -> v.severity() == VerificationSeverity.ERROR); }
    public boolean rewriteRequired() { return hasErrors() && rewriteCount == 0; }
    public VerificationResult withRewriteCount(int count) { return new VerificationResult(status, violations, count); }
}
