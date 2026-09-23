package com.dndmaster.adventure.evidence;
import java.util.List;
import java.util.Objects;
public record EvidenceRerankRequest(String policyId, String query, List<EvidenceCandidate> candidates) {
 public EvidenceRerankRequest { candidates=List.copyOf(Objects.requireNonNull(candidates,"candidates must not be null")); if(candidates.size()>180) throw new IllegalArgumentException("rerank candidates must contain at most 180 items"); }
}
