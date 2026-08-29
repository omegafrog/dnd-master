package com.dndmaster.adventure.domain.runtime.narrative;

public record RevealedFact(String factId, long turn, String source) {
    public RevealedFact {
        if (factId == null || factId.isBlank()) throw new IllegalArgumentException("revealed fact id must not be blank");
        if (turn < 0) throw new IllegalArgumentException("reveal turn must not be negative");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("reveal source must not be blank");
        factId = factId.trim(); source = source.trim();
    }
}
