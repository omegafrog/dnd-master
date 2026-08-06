package com.dndmaster.adventure.application.runtime;

import java.util.List;

public record RuntimeEvidenceProvenance(String candidateKey, double rerankScore, List<String> expandedKeys) {
    public RuntimeEvidenceProvenance { expandedKeys = List.copyOf(expandedKeys); }
}
