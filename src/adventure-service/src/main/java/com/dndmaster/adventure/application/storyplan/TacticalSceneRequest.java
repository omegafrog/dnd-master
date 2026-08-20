package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.List;
import java.util.Objects;

/** Typed retry request for one map-backed story stage. */
public record TacticalSceneRequest(AdventureStoryPlanStage stage, AdventureStoryPlanGenerationPort.MapContext map,
        List<AdventureStoryPlanGenerationPort.SourceCitation> citations, List<String> partyMemberIds,
        List<String> violations) {
    public TacticalSceneRequest(AdventureStoryPlanStage stage, AdventureStoryPlanGenerationPort.MapContext map,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations, List<String> violations) {
        this(stage, map, citations, List.of(), violations);
    }

    public TacticalSceneRequest {
        stage = Objects.requireNonNull(stage, "tactical stage must not be null");
        map = Objects.requireNonNull(map, "tactical map must not be null");
        citations = List.copyOf(Objects.requireNonNull(citations, "tactical citations must not be null"));
        partyMemberIds = List.copyOf(Objects.requireNonNull(partyMemberIds, "party member ids must not be null"));
        violations = List.copyOf(Objects.requireNonNull(violations, "tactical violations must not be null"));
    }

    public TacticalSceneRequest withViolations(List<String> nextViolations) {
        return new TacticalSceneRequest(stage, map, citations, partyMemberIds, nextViolations);
    }
}
