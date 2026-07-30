package com.dndmaster.ruleknowledge.application.search;

import com.dndmaster.ruleknowledge.domain.rulebook.*;
import java.util.*;

public record CharacterContextSearchQuery(
        OwnerPlayerId owner,
        Map<DocumentType, List<CharacterContextDocumentScope>> scope,
        Map<DocumentType, Double> thresholds,
        String situation,
        int tokenBudget) {
    public CharacterContextSearchQuery {
        Objects.requireNonNull(owner, "owner must not be null");
        scope = immutableScope(scope);
        thresholds = immutableThresholds(thresholds);
        if (situation == null || situation.isBlank()) throw new IllegalArgumentException("situation must not be blank");
        if (tokenBudget < 0) throw new IllegalArgumentException("token budget must not be negative");
    }

    private static Map<DocumentType, List<CharacterContextDocumentScope>> immutableScope(
            Map<DocumentType, List<CharacterContextDocumentScope>> value) {
        Objects.requireNonNull(value, "scope must not be null");
        EnumMap<DocumentType, List<CharacterContextDocumentScope>> result = new EnumMap<>(DocumentType.class);
        value.forEach((type, documents) -> result.put(Objects.requireNonNull(type), List.copyOf(documents)));
        return Map.copyOf(result);
    }

    private static Map<DocumentType, Double> immutableThresholds(Map<DocumentType, Double> value) {
        Objects.requireNonNull(value, "thresholds must not be null");
        EnumMap<DocumentType, Double> result = new EnumMap<>(DocumentType.class);
        value.forEach((type, threshold) -> {
            if (threshold == null || !Double.isFinite(threshold) || threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException("threshold must be between zero and one");
            }
            result.put(Objects.requireNonNull(type), threshold);
        });
        return Map.copyOf(result);
    }
}
