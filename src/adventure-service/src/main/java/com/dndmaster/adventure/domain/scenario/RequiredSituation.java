package com.dndmaster.adventure.domain.scenario;

import java.util.List;

/** A story-essential situation and only its source-explicit prerequisites. */
public record RequiredSituation(String situationId, String summary, List<String> prerequisiteSituationIds,
        List<ScenarioSourceReference> sourceRefs) {
    public RequiredSituation {
        if (situationId == null || situationId.isBlank() || summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("required situation id and summary are required");
        }
        prerequisiteSituationIds = List.copyOf(prerequisiteSituationIds);
        sourceRefs = List.copyOf(sourceRefs);
        if (sourceRefs.isEmpty() || prerequisiteSituationIds.contains(situationId)
                || prerequisiteSituationIds.stream().distinct().count() != prerequisiteSituationIds.size()) {
            throw new IllegalArgumentException("required situation needs source references and valid prerequisites");
        }
    }
}
