package com.dndmaster.aigamemaster.application.evidence;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record EvidenceRerankRequest(String query, String taskContext, List<EvidenceCandidate> candidates, UUID soloPlayerId) {
    public EvidenceRerankRequest(String query, String taskContext, List<EvidenceCandidate> candidates) {
        this(query, taskContext, candidates, null);
    }
    public EvidenceRerankRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        if (taskContext == null || taskContext.isBlank()) throw new IllegalArgumentException("taskContext is required");
        candidates = immutableCandidates(candidates, 180);
    }

    static List<EvidenceCandidate> immutableCandidates(List<EvidenceCandidate> values, int maximum) {
        if (values == null || values.size() > maximum) throw new IllegalArgumentException("candidate count is invalid");
        var copy = List.copyOf(values);
        if (copy.stream().anyMatch(java.util.Objects::isNull)
                || copy.stream().map(EvidenceCandidate::evidenceId).collect(java.util.stream.Collectors.toSet()).size() != copy.size()) {
            throw new IllegalArgumentException("candidate IDs must be unique");
        }
        return copy;
    }
}
