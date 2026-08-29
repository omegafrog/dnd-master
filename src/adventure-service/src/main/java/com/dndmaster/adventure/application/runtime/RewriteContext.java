package com.dndmaster.adventure.application.runtime;

import java.util.List;

public record RewriteContext(String originalDraft, List<VerificationViolation> violations,
                             String resolvedTurnFingerprint, int rewriteCount) {
    public RewriteContext {
        violations = List.copyOf(violations);
        if (rewriteCount != 0) throw new IllegalArgumentException("rewrite is only allowed once");
    }
}
