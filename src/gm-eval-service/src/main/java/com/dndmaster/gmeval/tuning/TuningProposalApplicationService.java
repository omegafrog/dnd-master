package com.dndmaster.gmeval.tuning;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Operator-facing read/evaluate entrypoint. No training or runtime configuration mutation. */
public final class TuningProposalApplicationService {
    private final TuningReadinessGate gate;
    private final TuningProposalRepository repository;

    public TuningProposalApplicationService(TuningProposalRepository repository) {
        this(new TuningReadinessGate(), repository);
    }

    public TuningProposalApplicationService(TuningReadinessGate gate, TuningProposalRepository repository) {
        this.gate = Objects.requireNonNull(gate, "tuning readiness gate required");
        this.repository = Objects.requireNonNull(repository, "tuning proposal repository required");
    }

    public TuningProposalView evaluate(TuningProposal proposal) {
        TuningEligibility eligibility = gate.evaluate(proposal);
        repository.save(TuningProposalRecord.from(proposal, eligibility));
        return repository.readProjection(proposal.proposalId());
    }

    public TuningProposalView get(String proposalId) { return repository.readProjection(proposalId); }
    public List<TuningProposalView> list() { return repository.listProjections(); }
    public List<TuningAuditEntry> audit(String proposalId) { return repository.audit(proposalId); }
    public Set<TuningFailureCategory> failureTaxonomy(String proposalId) { return repository.failureTaxonomy(proposalId); }
}
