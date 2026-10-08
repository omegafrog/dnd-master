package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;

@FunctionalInterface
public interface StageDetailedGenerationPort {
    DetailedStage generate(Request request);

    record Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
            long backboneRevision, List<ScenarioSourceReference> evidence) {
        public Request { evidence = List.copyOf(evidence); }
    }
}
