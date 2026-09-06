package com.dndmaster.adventure.domain.scenario;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record DetailedStage(UUID scenarioPackageId, long backboneRevision, String stageId, long revision,
                            String coreProblem, List<RevelationDefinition> revelations, ThreatDefinition threat,
                            PressureDefinition pressure, FunnelDefinition funnel,
                            List<SituationDefinition> situations, List<ScenarioSourceReference> sourceRefs) {
    public DetailedStage {
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (backboneRevision < 1 || revision < 1) throw new IllegalArgumentException("stage revisions must be positive");
        required(stageId, "stage id"); required(coreProblem, "core problem");
        revelations = List.copyOf(Objects.requireNonNull(revelations, "revelations must not be null"));
        threat = Objects.requireNonNull(threat, "threat must not be null");
        pressure = Objects.requireNonNull(pressure, "pressure must not be null");
        funnel = Objects.requireNonNull(funnel, "funnel must not be null");
        situations = List.copyOf(Objects.requireNonNull(situations, "situations must not be null"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "stage source refs must not be null"));
        Set<String> revelationIds = new HashSet<>();
        for (RevelationDefinition revelation : revelations) {
            if (!revelationIds.add(revelation.revelationId())) throw new IllegalArgumentException("revelation ids must be unique");
        }
        if (!revelationIds.containsAll(funnel.requiredRevelationIds())) throw new IllegalArgumentException("funnel references unknown revelation");
        Set<String> situationIds = new HashSet<>();
        for (SituationDefinition situation : situations) {
            if (!situationIds.add(situation.situationId())) throw new IllegalArgumentException("situation ids must be unique");
        }
    }
    private static void required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
