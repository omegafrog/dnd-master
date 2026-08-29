package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;

public final class DefaultNarrativeVerifier implements NarrativeVerifierPort {
    private final DeterministicNarrativeValidator deterministic;
    private final NarrativeVerifierPort semantic;

    public DefaultNarrativeVerifier(NarrativeVerifierPort semantic) {
        this.deterministic = new DeterministicNarrativeValidator();
        // A missing provider is still a real gate: deterministic grounding remains active.
        this.semantic = semantic == null ? (context, draft) -> {
            if (context.supportedFacts().isEmpty()) return VerificationResult.pass();
            boolean grounded = context.supportedFacts().stream().anyMatch(fact ->
                    !fact.isBlank() && draft.toLowerCase(java.util.Locale.ROOT)
                            .contains(fact.toLowerCase(java.util.Locale.ROOT)));
            return grounded ? VerificationResult.pass() : new VerificationResult(VerificationStatus.FAIL,
                    List.of(new VerificationViolation(VerificationViolationType.UNSUPPORTED_FACT,
                            VerificationSeverity.ERROR, "draft has no grounded claim", "semantic", "include a supported fact")), 0);
        } : semantic;
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
