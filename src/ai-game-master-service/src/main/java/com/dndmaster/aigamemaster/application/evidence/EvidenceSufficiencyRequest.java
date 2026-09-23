package com.dndmaster.aigamemaster.application.evidence;

import java.util.List;
import java.util.UUID;

public record EvidenceSufficiencyRequest(EvidenceTaskPolicy policy, String taskContext,
                                         List<EvidenceCandidate> candidates, List<String> pinnedEvidenceIds,
                                         UUID soloPlayerId) {
    public EvidenceSufficiencyRequest(EvidenceTaskPolicy policy, String taskContext,
                                      List<EvidenceCandidate> candidates, List<String> pinnedEvidenceIds) {
        this(policy, taskContext, candidates, pinnedEvidenceIds, null);
    }
    public EvidenceSufficiencyRequest {
        if (policy == null) throw new IllegalArgumentException("policy is required");
        if (taskContext == null || taskContext.isBlank()) throw new IllegalArgumentException("taskContext is required");
        candidates = EvidenceRerankRequest.immutableCandidates(candidates, 30);
        pinnedEvidenceIds = List.copyOf(pinnedEvidenceIds == null ? List.of() : pinnedEvidenceIds);
        var known = candidates.stream().map(EvidenceCandidate::evidenceId).collect(java.util.stream.Collectors.toSet());
        if (pinnedEvidenceIds.size() != new java.util.HashSet<>(pinnedEvidenceIds).size()
                || pinnedEvidenceIds.stream().anyMatch(id -> id == null || id.isBlank() || !known.contains(id))) {
            throw new IllegalArgumentException("pinned IDs must be unique candidate IDs");
        }
    }
}
