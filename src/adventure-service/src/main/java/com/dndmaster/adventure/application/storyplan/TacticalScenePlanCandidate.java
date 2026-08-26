package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.List;
import java.util.Objects;

/** Candidate returned by the AI boundary before source and scene validation. */
public record TacticalScenePlanCandidate(int stagePosition, TacticalScenePlan scene,
        List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
    public TacticalScenePlanCandidate {
        if (stagePosition < 1) throw new IllegalArgumentException("tactical candidate stage position must be positive");
        scene = Objects.requireNonNull(scene, "tactical candidate scene must not be null");
        citations = List.copyOf(Objects.requireNonNull(citations, "tactical candidate citations must not be null"));
    }

    public static TacticalScenePlanCandidate absent(int stagePosition) {
        return new TacticalScenePlanCandidate(stagePosition, TacticalScenePlan.absent(), List.of());
    }

    public static TacticalScenePlanCandidate ready(int stagePosition, TacticalScenePlan scene,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        return new TacticalScenePlanCandidate(stagePosition, scene, citations);
    }

    public static TacticalScenePlanCandidate withCitation(int stagePosition, TacticalScenePlan scene,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        return new TacticalScenePlanCandidate(stagePosition, scene, citations);
    }
}
