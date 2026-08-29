package com.dndmaster.gmeval.tuning;

import java.util.Objects;

/** Role-scoped request crossing the offline trainer port. */
public record TuningTrainingRequest(TuningProposal proposal, TuningEligibility eligibility,
                                   TrainingHyperparameters hyperparameters) {
    public TuningTrainingRequest {
        proposal = Objects.requireNonNull(proposal, "tuning proposal required");
        eligibility = Objects.requireNonNull(eligibility, "tuning eligibility required");
        hyperparameters = Objects.requireNonNull(hyperparameters, "training hyperparameters required");
        if (!eligibility.eligible() || !proposal.proposalId().equals(eligibility.proposalId())
                || proposal.role() != eligibility.role()) throw new IllegalArgumentException("eligible role proposal required");
        if (proposal.method() != hyperparameters.method()) throw new IllegalArgumentException("training method mismatch");
    }
}
