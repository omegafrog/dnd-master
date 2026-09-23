package com.dndmaster.aigamemaster.application.evidence;

import java.util.LinkedHashMap;
import java.util.List;

public record EvidenceRerankRequest(String query, String taskContext, List<EvidenceCandidate> candidates) {
    public EvidenceRerankRequest {
        if (query == null || query.isBlank()) throw new IllegalArgumentException("query is required");
        if (taskContext == null || taskContext.isBlank()) throw new IllegalArgumentException("taskContext is required");
        candidates = immutableCandidates(candidates, 180);
    }

    static List<EvidenceCandidate> immutableCandidates(List<EvidenceCandidate> values, int maximum) {
        if (values == null) throw new IllegalArgumentException("candidate count is invalid");
        var unique = new LinkedHashMap<String, EvidenceCandidate>();
        for (EvidenceCandidate candidate : values) {
            if (candidate == null) throw new IllegalArgumentException("candidate must not be null");
            unique.putIfAbsent(candidate.evidenceId(), candidate);
        }
        if (unique.size() > maximum) throw new IllegalArgumentException("candidate count is invalid");
        return List.copyOf(unique.values());
    }
}
