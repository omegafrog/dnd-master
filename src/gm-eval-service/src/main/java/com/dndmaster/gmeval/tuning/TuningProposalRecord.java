package com.dndmaster.gmeval.tuning;

import java.util.List;
import java.util.Objects;

/** Durable proposal plus immutable gate result and rejected audit evidence. */
public record TuningProposalRecord(TuningProposal proposal, TuningEligibility eligibility,
                                   List<TuningAuditEntry> audit) {
    public TuningProposalRecord {
        proposal = Objects.requireNonNull(proposal, "proposal required");
        eligibility = Objects.requireNonNull(eligibility, "eligibility required");
        if (!proposal.proposalId().equals(eligibility.proposalId()) || proposal.role() != eligibility.role()) {
            throw new IllegalArgumentException("proposal and eligibility identity mismatch");
        }
        audit = List.copyOf(audit == null ? List.of() : audit);
    }

    public static TuningProposalRecord from(TuningProposal proposal, TuningEligibility eligibility) {
        return new TuningProposalRecord(proposal, eligibility, List.of(TuningAuditEntry.from(eligibility)));
    }
}
