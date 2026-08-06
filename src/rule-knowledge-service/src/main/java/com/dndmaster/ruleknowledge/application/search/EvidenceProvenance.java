package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

public record EvidenceProvenance(String candidateKey, double rerankScore, List<String> expandedKeys) {
    public EvidenceProvenance {
        if (candidateKey == null || candidateKey.isBlank() || !Double.isFinite(rerankScore)) {
            throw new IllegalArgumentException("invalid evidence provenance");
        }
        expandedKeys = List.copyOf(expandedKeys);
    }
}
