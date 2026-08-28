package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.EffectiveGmProviderSelection;
import java.util.Objects;

/** Port result whose effective selection is bound to the invocation. */
public record GmCompletionResult<T>(T response, EffectiveGmProviderSelection effectiveSelection) {
    public GmCompletionResult {
        response = Objects.requireNonNull(response, "response required");
        effectiveSelection = Objects.requireNonNull(effectiveSelection, "effective selection required");
    }
}
