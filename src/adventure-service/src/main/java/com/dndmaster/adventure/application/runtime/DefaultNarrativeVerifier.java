package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;

public final class DefaultNarrativeVerifier implements NarrativeVerifierPort {
    private final DeterministicNarrativeValidator deterministic;
    private final NarrativeVerifierPort semantic;

    public DefaultNarrativeVerifier(NarrativeVerifierPort semantic) {
        this.deterministic = new DeterministicNarrativeValidator();
        this.semantic = semantic == null ? (context, draft) -> VerificationResult.pass() : semantic;
    }

    @Override
    public VerificationResult verify(NarrativeVerificationContext context, String draft) {
        var violations = new ArrayList<>(deterministic.validate(context, draft));
        VerificationResult semanticResult = semantic.verify(context, draft);
        violations.addAll(semanticResult.violations());
        return new VerificationResult(violations.stream().anyMatch(v -> v.severity() == VerificationSeverity.ERROR)
                ? VerificationStatus.FAIL : VerificationStatus.PASS, violations, 0);
    }
}
