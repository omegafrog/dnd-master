package com.dndmaster.adventure.domain.runtime.story;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** AI proposal that a future stage can no longer be reached as authored. */
public record PremiseInvalidationProposal(UUID proposalId, long expectedVersion, UUID scenarioPackageId,
        long backboneRevision, String targetStageId, String reason, Set<UUID> supportingFactIds) {
    public PremiseInvalidationProposal {
        Objects.requireNonNull(proposalId, "premise proposal id must not be null");
        if (expectedVersion < 0) throw new IllegalArgumentException("expected runtime version must not be negative");
        Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (backboneRevision < 1) throw new IllegalArgumentException("backbone revision must be positive");
        targetStageId = required(targetStageId, "target stage id");
        reason = reason == null ? "" : reason.trim();
        supportingFactIds = Set.copyOf(supportingFactIds == null ? Set.of() : supportingFactIds);
        if (supportingFactIds.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("supporting fact ids must not contain null");
    }

    public PremiseInvalidationProposal(UUID proposalId, long expectedVersion, UUID scenarioPackageId,
            long backboneRevision, String targetStageId, String reason, List<UUID> supportingFactIds) {
        this(proposalId, expectedVersion, scenarioPackageId, backboneRevision, targetStageId, reason,
                supportingFactIds == null ? Set.of() : Set.copyOf(supportingFactIds));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
