package com.dndmaster.aigamemaster.infrastructure.ai;

import java.util.Objects;

/** Provider output paired with the exact immutable selection used to produce it. */
public record GmCompletionResult<T>(T response, EffectiveGmProviderSelection effectiveSelection) {
    public GmCompletionResult {
        response = Objects.requireNonNull(response, "response required");
        effectiveSelection = Objects.requireNonNull(effectiveSelection, "effective selection required");
    }
}
