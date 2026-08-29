package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic gate result and the exact sample/audit decisions behind it. */
public record TuningEligibility(String proposalId, PromptRole role, TuningProposalStatus status,
                                Set<TuningRejectionReason> rejectionReasons, List<TuningSample> eligibleSamples,
                                List<SampleExclusion> exclusions, Set<TuningFailureCategory> failureTaxonomy,
                                String decisionFingerprint) {
    public TuningEligibility {
        proposalId = required(proposalId, "proposal id");
        role = Objects.requireNonNull(role, "eligibility role required");
        status = Objects.requireNonNull(status, "eligibility status required");
        rejectionReasons = rejectionReasons == null || rejectionReasons.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(rejectionReasons));
        eligibleSamples = List.copyOf(eligibleSamples == null ? List.of() : eligibleSamples);
        exclusions = List.copyOf(exclusions == null ? List.of() : exclusions);
        failureTaxonomy = failureTaxonomy == null || failureTaxonomy.isEmpty()
                ? Set.of() : Set.copyOf(EnumSet.copyOf(failureTaxonomy));
        decisionFingerprint = required(decisionFingerprint, "decision fingerprint");
    }

    public boolean eligible() { return status == TuningProposalStatus.ELIGIBLE; }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
        return value;
    }
}
