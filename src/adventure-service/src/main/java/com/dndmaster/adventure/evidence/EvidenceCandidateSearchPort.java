package com.dndmaster.adventure.evidence;
import java.util.List;
@FunctionalInterface
public interface EvidenceCandidateSearchPort {
    List<EvidenceCandidate> search(EvidenceCandidateSearchRequest request);
}
