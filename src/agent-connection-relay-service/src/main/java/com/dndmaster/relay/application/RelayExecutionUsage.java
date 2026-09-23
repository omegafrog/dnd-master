package com.dndmaster.relay.application;

/** Reported counts only; null denotes a value absent from the provider response. */
public record RelayExecutionUsage(Long inputTokens, Long cachedInputTokens, Long outputTokens) {
    public static RelayExecutionUsage unknown() { return new RelayExecutionUsage(null, null, null); }

    public RelayExecutionUsage {
        if ((inputTokens != null && inputTokens < 0) || (cachedInputTokens != null && cachedInputTokens < 0)
                || (outputTokens != null && outputTokens < 0))
            throw new IllegalArgumentException("provider usage must be nonnegative");
    }
}
