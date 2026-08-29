package com.dndmaster.gmeval.registry;

import java.util.List;
import java.util.Objects;

/** Durable evidence and state for one candidate approval workflow. */
public record PromptApprovalRecord(PromptVersion promptVersion, String candidateId, String optimizationRunId,
                                   HoldoutEvaluation holdout, ReviewerDecision review,
                                   PromptArtifactStatus status, List<PromptAuditEntry> audit) {
    public PromptApprovalRecord {
        promptVersion = Objects.requireNonNull(promptVersion, "approval prompt version required");
        if (candidateId == null || candidateId.isBlank()) throw new IllegalArgumentException("approval candidate id required");
        if (optimizationRunId == null || optimizationRunId.isBlank()) throw new IllegalArgumentException("approval run id required");
        holdout = Objects.requireNonNull(holdout, "holdout evidence required");
        if (status != PromptArtifactStatus.PENDING_REVIEW && status != PromptArtifactStatus.APPROVED
                && status != PromptArtifactStatus.ACTIVE && status != PromptArtifactStatus.ROLLED_BACK) {
            throw new IllegalArgumentException("invalid approval status");
        }
        audit = List.copyOf(audit == null ? List.of() : audit);
    }

    public PromptApprovalRecord withReview(ReviewerDecision nextReview, PromptArtifactStatus nextStatus,
                                           PromptAuditEntry entry) {
        List<PromptAuditEntry> nextAudit = new java.util.ArrayList<>(audit);
        nextAudit.add(Objects.requireNonNull(entry, "audit entry required"));
        return new PromptApprovalRecord(promptVersion, candidateId, optimizationRunId, holdout,
                nextReview, nextStatus, nextAudit);
    }

    public PromptApprovalRecord withStatus(PromptArtifactStatus nextStatus, PromptAuditEntry entry) {
        List<PromptAuditEntry> nextAudit = new java.util.ArrayList<>(audit);
        nextAudit.add(Objects.requireNonNull(entry, "audit entry required"));
        return new PromptApprovalRecord(promptVersion, candidateId, optimizationRunId, holdout,
                review, nextStatus, nextAudit);
    }
}
