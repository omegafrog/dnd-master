package com.dndmaster.adventure.application.scenario.preparation;

import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;

@FunctionalInterface
public interface StageArtifactPreparationPort {
    StageArtifactPreparationApplicationService.Result prepare(AdventureId adventureId, SessionId sessionId,
            UUID scenarioPackageId);
}
