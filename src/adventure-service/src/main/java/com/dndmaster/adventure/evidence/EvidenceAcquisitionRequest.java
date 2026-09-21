package com.dndmaster.adventure.evidence;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EvidenceAcquisitionRequest(String policyId, String query, List<UUID> pinnedEvidenceIds,
                                         EvidenceSearchScope searchScope) {
    public EvidenceAcquisitionRequest(String policyId, String query, List<UUID> pinnedEvidenceIds) {
        this(policyId, query, pinnedEvidenceIds, null);
    }
    public EvidenceAcquisitionRequest {
        if (policyId == null || policyId.isBlank()) throw new IllegalArgumentException("policy id must not be blank");
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query must not be blank");
        pinnedEvidenceIds = List.copyOf(Objects.requireNonNull(pinnedEvidenceIds, "pinned evidence ids must not be null"));
    }
}
