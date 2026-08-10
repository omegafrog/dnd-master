package com.dndmaster.ruleknowledge.domain.document.evidence;

import java.util.List;

public record StructuralEvidenceExtractionResult(List<StructuralEvidence> evidence,
                                                List<NavigationEntry> navigationEntries,
                                                List<String> diagnostics) {
    public StructuralEvidenceExtractionResult {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        navigationEntries = navigationEntries == null ? List.of() : List.copyOf(navigationEntries);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
