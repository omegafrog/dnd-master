package com.dndmaster.adventure.evidence;

import java.util.Objects;

/** Search input for one initial or additional evidence search. */
public record EvidenceCandidateSearchRequest(EvidenceAcquisitionRequest acquisitionRequest, String query,
                                             int additionalSearches) {
    public EvidenceCandidateSearchRequest {
        Objects.requireNonNull(acquisitionRequest, "acquisition request must not be null");
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("search query must not be blank");
        }
        if (additionalSearches < 0 || additionalSearches > 2) {
            throw new IllegalArgumentException("additional searches must be from zero through two");
        }
    }
}
