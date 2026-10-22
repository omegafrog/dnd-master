package com.dndmaster.adventure.application.scenario.preparation;

import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.StoryLength;

@FunctionalInterface
public interface StageArtifactPreparationPort {
    StageArtifactPreparationApplicationService.Result prepare(AdventureId adventureId, SessionId sessionId,
            UUID scenarioPackageId);

    default StageArtifactPreparationApplicationService.Result prepare(AdventureId adventureId, SessionId sessionId,
            UUID scenarioPackageId, StoryLength storyLength) {
        return prepare(adventureId, sessionId, scenarioPackageId);
    }
}
