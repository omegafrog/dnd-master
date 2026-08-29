package com.dndmaster.gmeval.tuning;

import java.util.List;
import java.util.Objects;

public record TuningAuditEntry(TuningAuditAction action, String proposalId,
                               TuningProposalStatus status, List<TuningRejectionReason> reasons,
                               String decisionFingerprint) {
    public TuningAuditEntry {
        action = Objects.requireNonNull(action, "audit action required");
        if (proposalId == null || proposalId.isBlank()) throw new IllegalArgumentException("audit proposal id required");
        status = Objects.requireNonNull(status, "audit status required");
        reasons = List.copyOf(reasons == null ? List.of() : reasons);
        if (decisionFingerprint == null || decisionFingerprint.isBlank()) throw new IllegalArgumentException("audit fingerprint required");
    }

    public static TuningAuditEntry from(TuningEligibility eligibility) {
        TuningAuditAction action = eligibility.eligible() ? TuningAuditAction.ELIGIBLE : TuningAuditAction.REJECTED;
        return new TuningAuditEntry(action, eligibility.proposalId(), eligibility.status(),
                eligibility.rejectionReasons().stream().sorted().toList(), eligibility.decisionFingerprint());
    }
}
