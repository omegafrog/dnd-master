package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.story.PremiseInvalidationResult;
import com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import java.util.Objects;

/** Result of a verified replan attempt, including the decision audit and immutable artifact. */
public record StoryRuntimeReplanResult(PremiseInvalidationResult decision, StageBackbone backbone,
        StoryRuntimeState state) {
    public StoryRuntimeReplanResult {
        decision = Objects.requireNonNull(decision, "replan decision must not be null");
        backbone = Objects.requireNonNull(backbone, "replan backbone must not be null");
        state = Objects.requireNonNull(state, "replan state must not be null");
    }
}
