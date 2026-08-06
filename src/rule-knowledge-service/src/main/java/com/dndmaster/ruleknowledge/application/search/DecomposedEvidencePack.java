package com.dndmaster.ruleknowledge.application.search;

import java.util.Map;

public record DecomposedEvidencePack(Map<DecomposedIntent, HybridRetrievalResult> byIntent) {
    public DecomposedEvidencePack { byIntent = Map.copyOf(byIntent); }
    public boolean degraded() { return byIntent.values().stream().anyMatch(HybridRetrievalResult::degraded); }
}
