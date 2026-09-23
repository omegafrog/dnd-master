package com.dndmaster.aigamemaster.application.ai;

/** Provider-reported token counts for one call. Null means the provider did not report that count. */
public record AiExecutionUsage(Long inputTokens, Long cachedInputTokens, Long outputTokens) {
    public static AiExecutionUsage unknown() { return new AiExecutionUsage(null, null, null); }

    public AiExecutionUsage {
        if ((inputTokens != null && inputTokens < 0) || (cachedInputTokens != null && cachedInputTokens < 0)
                || (outputTokens != null && outputTokens < 0))
            throw new IllegalArgumentException("provider usage must be nonnegative");
    }
}
