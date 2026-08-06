package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

@FunctionalInterface
public interface ContextExpansionPort {
    List<HybridRetrievalCandidate> expand(HybridRetrievalCandidate candidate, RetrievalScope scope, int radius);

    static ContextExpansionPort identity() {
        return (candidate, scope, radius) -> List.of(candidate);
    }
}
