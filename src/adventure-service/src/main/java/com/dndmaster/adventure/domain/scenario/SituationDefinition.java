package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record SituationDefinition(String situationId, List<StageIntent> intents,
                                  List<ScenarioSourceReference> sourceRefs) {
    public SituationDefinition {
        if (situationId == null || situationId.isBlank()) throw new IllegalArgumentException("situation id is required");
        intents = List.copyOf(Objects.requireNonNull(intents, "situation intents must not be null"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "situation source refs must not be null"));
        if (intents.isEmpty()) throw new IllegalArgumentException("situation must have a stage intent");
        if (sourceRefs.isEmpty()) throw new IllegalArgumentException("situation needs grounding");
    }
}
