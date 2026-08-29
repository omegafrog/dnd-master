package com.dndmaster.adventure.application.runtime;

import java.util.List;

public record NarrativeVerificationAudit(String turnId, String resolvedTurnFingerprint,
                                         VerificationResult initialResult, VerificationResult finalResult,
                                         boolean rewriteAttempted, List<String> modelMetadata) {
    public NarrativeVerificationAudit {
        modelMetadata = List.copyOf(modelMetadata);
    }
}
