package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

public record EvidencePackEntry(HybridRetrievalCandidate candidate, List<HybridRetrievalCandidate> context,
        EvidenceProvenance provenance) {
    public EvidencePackEntry {
        context = List.copyOf(context);
    }
}
