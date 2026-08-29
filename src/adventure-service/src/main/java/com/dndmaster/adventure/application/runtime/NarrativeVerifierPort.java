package com.dndmaster.adventure.application.runtime;

public interface NarrativeVerifierPort {
    VerificationResult verify(NarrativeVerificationContext context, String draft);
}
