package com.dndmaster.adventure.domain.runtime.story;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Structured audit entry for every invalidation decision, including deterministic rejects. */
public record PremiseInvalidationAudit(UUID proposalId, String targetStageId, boolean accepted, Reason reason,
        long expectedVersion, long backboneRevision, Set<UUID> supportingFactIds) {
    public enum Reason {
        ACCEPTED,
        DUPLICATE_PROPOSAL,
        STALE_RUNTIME_VERSION,
        STALE_BACKBONE_REVISION,
        TARGET_STAGE_NOT_FOUND,
        TARGET_STAGE_ALREADY_STARTED,
        NO_ACCEPTED_FACT,
        UNKNOWN_SUPPORTING_FACT,
        INVALIDATION_REASON_MISSING
    }

    public PremiseInvalidationAudit {
        Objects.requireNonNull(proposalId, "audit proposal id must not be null");
        if (targetStageId == null || targetStageId.isBlank()) throw new IllegalArgumentException("audit target stage is required");
        targetStageId = targetStageId.trim();
        reason = Objects.requireNonNull(reason, "audit reason must not be null");
        if (expectedVersion < 0 || backboneRevision < 1) throw new IllegalArgumentException("audit versions are invalid");
        supportingFactIds = Set.copyOf(supportingFactIds == null ? Set.of() : supportingFactIds);
    }
}
