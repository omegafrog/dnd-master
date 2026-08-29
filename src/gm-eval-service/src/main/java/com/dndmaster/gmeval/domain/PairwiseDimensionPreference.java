package com.dndmaster.gmeval.domain;
public record PairwiseDimensionPreference(String dimension, PairwisePreference preference, String reason, String evidence) {
    public PairwiseDimensionPreference {
        if (dimension == null || dimension.isBlank() || preference == null || reason == null || reason.isBlank()
                || evidence == null || evidence.isBlank()) throw new IllegalArgumentException("pairwise preference fields required");
    }
}
