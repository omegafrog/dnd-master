package com.dndmaster.aigamemaster.domain.turnplan;

import java.util.HashSet;
import java.util.List;

public record InformationPolicy(List<String> requiredFacts, List<String> revealableFacts, List<String> forbiddenFacts) {
    public InformationPolicy {
        requiredFacts = normalizedUnique(requiredFacts, "requiredFacts");
        revealableFacts = normalizedUnique(revealableFacts, "revealableFacts");
        forbiddenFacts = normalizedUnique(forbiddenFacts, "forbiddenFacts");
    }
    private static List<String> normalizedUnique(List<String> values, String name) {
        List<String> normalized = TurnPlan.copy(values, name).stream().map(v -> TurnPlan.required(v, "Fact ID")).toList();
        if (normalized.size() != new HashSet<>(normalized).size()) throw new IllegalArgumentException(name + " contains duplicates");
        return normalized;
    }
}
