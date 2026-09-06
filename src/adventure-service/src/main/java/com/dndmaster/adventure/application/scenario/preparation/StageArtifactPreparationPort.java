package com.dndmaster.adventure.application.scenario.preparation;

import java.util.UUID;

@FunctionalInterface
public interface StageArtifactPreparationPort {
    StageArtifactPreparationApplicationService.Result prepare(UUID scenarioPackageId);
}
