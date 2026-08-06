package com.dndmaster.ruleknowledge.application.search;

import java.util.List;

public record HybridRetrievalResult(List<HybridRetrievalCandidate> candidates, boolean degraded, String status) {
    public HybridRetrievalResult { candidates = List.copyOf(candidates); }
}
