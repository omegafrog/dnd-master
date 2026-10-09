package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

/** Retrieves dense candidates only from the caller-authorized published document scope. */
public interface DenseEvidenceCandidateSearchPort {
    List<EvidenceCandidate> search(EvidenceSearchRequest request);

    default List<List<EvidenceCandidate>> searchBatch(List<EvidenceSearchRequest> requests) {
        return requests.stream().map(this::search).toList();
    }
}
