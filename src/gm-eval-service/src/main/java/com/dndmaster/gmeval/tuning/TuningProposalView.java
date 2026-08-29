package com.dndmaster.gmeval.tuning;

import com.dndmaster.gmeval.registry.PromptRole;
import java.util.List;
import java.util.Set;

/** Read-only operator projection; it exposes decisions, not a mutation endpoint. */
public record TuningProposalView(String proposalId, PromptRole role, TuningMethod method,
                                 TuningProposalStatus status, boolean eligible,
                                 Set<TuningRejectionReason> rejectionReasons, List<String> eligibleSampleIds,
                                 List<SampleExclusion> exclusions, Set<TuningFailureCategory> failureTaxonomy,
                                 List<TuningAuditEntry> audit, String decisionFingerprint) {
    public static TuningProposalView from(TuningProposalRecord record) {
        TuningEligibility eligibility = record.eligibility();
        return new TuningProposalView(record.proposal().proposalId(), record.proposal().role(), record.proposal().method(),
                eligibility.status(), eligibility.eligible(), eligibility.rejectionReasons(),
                eligibility.eligibleSamples().stream().map(TuningSample::sampleId).toList(), eligibility.exclusions(),
                eligibility.failureTaxonomy(), record.audit(), eligibility.decisionFingerprint());
    }
}
