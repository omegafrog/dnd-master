package com.dndmaster.aigamemaster.domain.turnplan;

import java.util.HashSet;
import java.util.List;

public record StoryProgress(Boolean advanceStage, List<String> triggeredConditions) {
    public StoryProgress {
        if (advanceStage == null) throw new IllegalArgumentException("advanceStage must not be null");
        triggeredConditions = TurnPlan.copy(triggeredConditions, "triggeredConditions").stream().map(v -> TurnPlan.required(v, "condition ID")).toList();
        if (triggeredConditions.size() != new HashSet<>(triggeredConditions).size()) throw new IllegalArgumentException("triggeredConditions contains duplicates");
    }
}
