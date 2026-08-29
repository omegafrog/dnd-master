package com.dndmaster.gmeval.registry;

import com.dndmaster.gmeval.optimization.PromptOptimizationRun;
import java.util.List;
import java.util.Objects;

/** Operator entrypoint for review, activation, and rollback commands. */
public final class PromptApprovalApplicationService {
    private final PromptRegistry registry;

    public PromptApprovalApplicationService(PromptRegistry registry) { this.registry = Objects.requireNonNull(registry, "prompt registry required"); }
    public PromptArtifact submitForReview(PromptOptimizationRun run, String candidateId, HoldoutEvaluation holdout) { return registry.submitForReview(run, candidateId, holdout); }
    public PromptArtifact decide(PromptVersion version, ReviewerDecision decision) { return registry.review(version, decision); }
    public PromptRuntimeConfiguration activate(PromptVersion version, PromptVersion expectedActiveVersion, String actor) { return registry.activate(version, expectedActiveVersion, actor); }
    public PromptRuntimeConfiguration rollback(PromptRole role, PromptVersion targetVersion, PromptVersion expectedActiveVersion, String actor) { return registry.rollback(role, targetVersion, expectedActiveVersion, actor); }
    public List<PromptAuditEntry> audit() { return registry.audit(); }
}
