package com.dndmaster.ruleknowledge.application.search;

import java.util.EnumMap;
import java.util.Objects;

public final class DecomposedRetrievalService {
    private final HybridRetrievalService retrieval;
    public DecomposedRetrievalService(HybridRetrievalService retrieval) { this.retrieval = Objects.requireNonNull(retrieval); }
    public DecomposedEvidencePack retrieve(String action, RetrievalScope scope, int limit) {
        EnumMap<DecomposedIntent, HybridRetrievalResult> results = new EnumMap<>(DecomposedIntent.class);
        for (QueryIntentPart part : QueryDecomposer.decompose(action)) {
            results.put(part.intent(), retrieval.retrieve(part.query(), scope.withDocumentTypes(documentTypes(part.intent())), limit));
        }
        return new DecomposedEvidencePack(results);
    }

    private static java.util.Set<com.dndmaster.ruleknowledge.domain.rulebook.DocumentType> documentTypes(DecomposedIntent intent) {
        return switch (intent) {
            case RULES, COMBAT, RESOURCES -> java.util.Set.of(com.dndmaster.ruleknowledge.domain.rulebook.DocumentType.RULEBOOK);
            case SCENE, NPC, CONTINUITY -> java.util.Set.of(com.dndmaster.ruleknowledge.domain.rulebook.DocumentType.STORYBOOK);
        };
    }
}
