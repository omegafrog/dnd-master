package com.dndmaster.adventure.evidence;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
public record EvidenceRerankRequest(String policyId, String query, List<EvidenceCandidate> candidates, UUID soloPlayerId) {
 public EvidenceRerankRequest(String policyId, String query, List<EvidenceCandidate> candidates) {
  this(policyId, query, candidates, null);
 }
 public EvidenceRerankRequest {
  candidates=deduplicate(Objects.requireNonNull(candidates,"candidates must not be null"));
  if(candidates.size()>180) throw new IllegalArgumentException("rerank candidates must contain at most 180 items");
 }

 private static List<EvidenceCandidate> deduplicate(List<EvidenceCandidate> values) {
  LinkedHashMap<UUID, EvidenceCandidate> unique = new LinkedHashMap<>();
  for (EvidenceCandidate candidate : values) {
   Objects.requireNonNull(candidate, "rerank candidates must not contain null");
   unique.putIfAbsent(candidate.id(), candidate);
  }
  return List.copyOf(unique.values());
 }
}
