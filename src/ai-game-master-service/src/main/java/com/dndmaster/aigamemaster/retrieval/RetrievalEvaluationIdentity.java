package com.dndmaster.aigamemaster.retrieval;

public record RetrievalEvaluationIdentity(
        String corpusDigest,
        String embeddingModel,
        String indexVersion,
        String serviceVersion,
        String configurationDigest) {
    public RetrievalEvaluationIdentity {
        required(corpusDigest, "corpusDigest");
        required(embeddingModel, "embeddingModel");
        required(indexVersion, "indexVersion");
        required(serviceVersion, "serviceVersion");
        required(configurationDigest, "configurationDigest");
    }

    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " required");
    }
}
