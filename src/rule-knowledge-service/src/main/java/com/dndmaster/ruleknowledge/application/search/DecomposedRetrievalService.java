package com.dndmaster.ruleknowledge.application.search;

import java.util.EnumMap;
import java.util.Objects;

public final class DecomposedRetrievalService {
    private final HybridRetrievalService retrieval;
    public DecomposedRetrievalService(HybridRetrievalService retrieval) { this.retrieval = Objects.requireNonNull(retrieval); }
    public DecomposedEvidencePack retrieve(String action, RetrievalScope scope, int limit) {
        EnumMap<DecomposedIntent, HybridRetrievalResult> results = new EnumMap<>(DecomposedIntent.class);
        for (QueryIntentPart part : QueryDecomposer.decompose(action)) {
            results.put(part.intent(), retrieval.retrieve(part.query(), scope, limit));
        }
        return new DecomposedEvidencePack(results);
    }
}
