package com.dndmaster.aigamemaster.infrastructure.ai;

/** Small, payload-free helpers shared by provider instrumentation. */
public final class AiCallObservability {
    private AiCallObservability() { }

    public static int estimatedTokens(int promptChars) {
        return promptChars <= 0 ? 0 : (promptChars + 3) / 4;
    }

    public static String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.replaceAll("[^A-Za-z0-9._:-]", "_");
    }
}
