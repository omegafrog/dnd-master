package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.List;
import java.util.Objects;

/** Evidence package submitted to the fine-tuning readiness gate. */
public record TuningProposal(String proposalId, PromptRole role, TuningMethod method,
                             String stableContractVersion, String evalVersion, String datasetVersion,
                             String holdoutVersion, String baseModelVersion, String optimizedPromptVersion,
                             boolean stableContractPresent, boolean evalPresent, boolean baselinePresent,
                             boolean optimizedPromptPresent, List<TuningSample> samples,
                             List<FailureEvidence> failureEvidence, TuningComparison comparison) {
    public TuningProposal {
        proposalId = required(proposalId, "proposal id");
        role = Objects.requireNonNull(role, "proposal role required");
        method = Objects.requireNonNull(method, "tuning method required");
        stableContractVersion = required(stableContractVersion, "stable contract version");
        evalVersion = required(evalVersion, "eval version");
        datasetVersion = required(datasetVersion, "dataset version");
        holdoutVersion = required(holdoutVersion, "holdout version");
        baseModelVersion = required(baseModelVersion, "base model version");
        optimizedPromptVersion = required(optimizedPromptVersion, "optimized prompt version");
        samples = List.copyOf(samples == null ? List.of() : samples);
        failureEvidence = List.copyOf(failureEvidence == null ? List.of() : failureEvidence);
        comparison = Objects.requireNonNull(comparison, "tuning comparison required");
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value.trim();
    }
}
