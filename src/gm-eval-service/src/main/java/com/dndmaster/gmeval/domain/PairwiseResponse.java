package com.dndmaster.gmeval.domain;
public record PairwiseResponse(String caseId, int schemaVersion, String response) {
    public PairwiseResponse { if (caseId == null || caseId.isBlank() || schemaVersion < 1 || response == null || response.isBlank()) throw new IllegalArgumentException("pairwise response required"); }
}
