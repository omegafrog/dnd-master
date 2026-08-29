package com.dndmaster.adventure.application.runtime;

/** Deterministic production rewrite that removes claims identified by the verification gate. */
public final class BoundedNarrativeRewriteAdapter implements RewritePort {
    @Override
    public WriterProse rewrite(RewriteContext context) {
        String rewritten = context.originalDraft();
        for (VerificationViolation violation : context.violations()) {
            if (violation.evidence() != null && !violation.evidence().isBlank()) {
                rewritten = rewritten.replace(violation.evidence(), "");
            }
        }
        return new WriterProse(rewritten.trim());
    }
}
