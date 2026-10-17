package com.dndmaster.adventure.evidence;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
public record EvidenceRerankRequest(String policyId, String query, List<EvidenceCandidate> candidates, UUID soloPlayerId, int resultLimit) {
 public EvidenceRerankRequest(String policyId, String query, List<EvidenceCandidate> candidates, UUID soloPlayerId) { this(policyId, query, candidates, soloPlayerId, "PLAYER_ACTION".equals(policyId) ? 20 : 30); }
 public EvidenceRerankRequest(String policyId, String query, List<EvidenceCandidate> candidates) { this(policyId, query, candidates, null); }
 public EvidenceRerankRequest { candidates=List.copyOf(Objects.requireNonNull(candidates,"candidates must not be null")); if(candidates.size()>180) throw new IllegalArgumentException("rerank candidates must contain at most 180 items"); if(resultLimit<1||resultLimit>30||("PLAYER_ACTION".equals(policyId)&&resultLimit!=20)) throw new IllegalArgumentException("rerank result limit is invalid"); }
}
