package com.dndmaster.adventure.domain.scenario;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record StageBackbone(UUID scenarioPackageId, long revision, List<StageBackboneEntry> stages,
                            List<ScenarioSourceReference> sourceRefs, List<RequiredSituation> requiredSituations) {
    public StageBackbone(UUID scenarioPackageId, long revision, List<StageBackboneEntry> stages,
            List<ScenarioSourceReference> sourceRefs) {
        this(scenarioPackageId, revision, stages, sourceRefs, List.of());
    }

    public StageBackbone {
        scenarioPackageId = Objects.requireNonNull(scenarioPackageId, "scenario package id must not be null");
        if (revision < 1) throw new IllegalArgumentException("backbone revision must be positive");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
        sourceRefs = List.copyOf(Objects.requireNonNull(sourceRefs, "backbone source refs must not be null"));
        requiredSituations = List.copyOf(Objects.requireNonNull(requiredSituations, "required situations must not be null"));
        if (stages.isEmpty()) throw new IllegalArgumentException("backbone must contain a stage");
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < stages.size(); index++) {
            StageBackboneEntry stage = stages.get(index);
            if (!ids.add(stage.stageId())) throw new IllegalArgumentException("stage ids must be unique");
            if (stage.order() != index + 1) throw new IllegalArgumentException("stage order must be contiguous");
        }
    }
}
