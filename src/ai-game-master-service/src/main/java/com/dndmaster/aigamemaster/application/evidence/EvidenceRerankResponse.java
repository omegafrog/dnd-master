package com.dndmaster.aigamemaster.application.evidence;

import java.util.List;

public record EvidenceRerankResponse(List<String> orderedCandidateIds) {
    public EvidenceRerankResponse { orderedCandidateIds = List.copyOf(orderedCandidateIds); }
}
