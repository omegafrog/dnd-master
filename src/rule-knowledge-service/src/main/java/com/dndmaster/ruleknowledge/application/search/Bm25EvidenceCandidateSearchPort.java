package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

public interface Bm25EvidenceCandidateSearchPort {
    List<EvidenceCandidate> search(EvidenceSearchRequest request);
}
