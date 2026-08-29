package com.dndmaster.adventure.application.runtime;

import java.util.List;

/** Production fallback semantic gate; provider-backed implementations may replace it in tests/deployment. */
public final class DeterministicSemanticNarrativeVerifier implements NarrativeVerifierPort {
    @Override
    public VerificationResult verify(NarrativeVerificationContext context, String draft) {
        if (context.supportedFacts().isEmpty()) return VerificationResult.pass();
        boolean grounded = context.supportedFacts().stream().anyMatch(fact ->
                !fact.isBlank() && draft.toLowerCase(java.util.Locale.ROOT)
                        .contains(fact.toLowerCase(java.util.Locale.ROOT)));
        return grounded ? VerificationResult.pass() : new VerificationResult(VerificationStatus.FAIL,
                List.of(new VerificationViolation(VerificationViolationType.UNSUPPORTED_FACT,
                        VerificationSeverity.ERROR, "draft has no grounded claim", "semantic",
                        "include a supported fact")), 0);
    }
}
