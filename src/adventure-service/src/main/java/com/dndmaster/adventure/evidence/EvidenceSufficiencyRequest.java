package com.dndmaster.adventure.evidence;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
public record EvidenceSufficiencyRequest(String policyId, String query, List<EvidenceCandidate> candidates, List<UUID> pinnedEvidenceIds, int additionalSearches, UUID soloPlayerId) {
 public EvidenceSufficiencyRequest(String policyId, String query, List<EvidenceCandidate> candidates, List<UUID> pinnedEvidenceIds, int additionalSearches) { this(policyId, query, candidates, pinnedEvidenceIds, additionalSearches, null); }
 public EvidenceSufficiencyRequest { candidates=List.copyOf(Objects.requireNonNull(candidates,"candidates must not be null")); pinnedEvidenceIds=List.copyOf(Objects.requireNonNull(pinnedEvidenceIds,"pinned evidence ids must not be null")); if(candidates.size()>30) throw new IllegalArgumentException("judge candidates must contain at most 30 items"); }
}
