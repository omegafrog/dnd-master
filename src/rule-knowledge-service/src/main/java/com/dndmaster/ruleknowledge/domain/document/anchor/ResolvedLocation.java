package com.dndmaster.ruleknowledge.domain.document.anchor;

import java.util.OptionalInt;

public record ResolvedLocation(String rawLocator, OptionalInt physicalPage, double confidence, String strategy) {
    public ResolvedLocation {
        rawLocator = rawLocator == null ? "" : rawLocator;
        physicalPage = physicalPage == null ? OptionalInt.empty() : physicalPage;
        strategy = strategy == null ? "unresolved" : strategy;
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
    }
}
