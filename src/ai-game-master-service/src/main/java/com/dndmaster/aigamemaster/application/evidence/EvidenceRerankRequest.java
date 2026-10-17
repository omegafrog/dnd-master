package com.dndmaster.aigamemaster.application.evidence;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public record EvidenceRerankRequest(String query, String taskContext, List<EvidenceCandidate> candidates, UUID soloPlayerId, int resultLimit) {
    public EvidenceRerankRequest(String query, String taskContext, List<EvidenceCandidate> candidates, UUID soloPlayerId) {
        this(query, taskContext, candidates, soloPlayerId, "PLAYER_ACTION".equals(taskContext) ? 20 : 30);
    }
    public EvidenceRerankRequest(String query, String taskContext, List<EvidenceCandidate> candidates) {
        this(query, taskContext, candidates, null);
    }
    public EvidenceRerankRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        if (taskContext == null || taskContext.isBlank()) throw new IllegalArgumentException("taskContext is required");
        if (resultLimit < 1 || resultLimit > 30 || ("PLAYER_ACTION".equals(taskContext) && resultLimit != 20)) throw new IllegalArgumentException("resultLimit is invalid");
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
