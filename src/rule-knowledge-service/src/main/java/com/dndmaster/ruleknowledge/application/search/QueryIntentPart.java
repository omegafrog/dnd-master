package com.dndmaster.ruleknowledge.application.search;

public record QueryIntentPart(DecomposedIntent intent, String query) {
    public QueryIntentPart { if (intent == null || query == null || query.isBlank()) throw new IllegalArgumentException("invalid query intent part"); }
}
