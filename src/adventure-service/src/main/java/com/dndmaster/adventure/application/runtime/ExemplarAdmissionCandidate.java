package com.dndmaster.adventure.application.runtime;

import java.util.Objects;

public record ExemplarAdmissionCandidate(StyleExemplar exemplar, VerificationResult verification,
        boolean containsRuntimePollution, boolean sessionScoped) {
    public ExemplarAdmissionCandidate {
        exemplar = Objects.requireNonNull(exemplar); verification = Objects.requireNonNull(verification);
    }
}
