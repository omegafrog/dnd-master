package com.dndmaster.adventure.application.runtime;

/** Bounded decision policy: warning-only output is publishable; errors get one rewrite. */
public class VerificationPolicy {
    public boolean accepts(VerificationResult result) {
        return !result.hasErrors();
    }

    public boolean requiresRewrite(VerificationResult result) {
        return result.rewriteRequired();
    }
}
