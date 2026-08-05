package com.dndmaster.adventure.application.runtime;

public record ContextUsage(long estimatedTokens, long contextLimit) {
    public ContextUsage {
        if (estimatedTokens < 0 || contextLimit <= 0 || estimatedTokens > contextLimit) {
            throw new IllegalArgumentException("invalid context usage");
        }
    }
}
