package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface StageDetailedGenerationPort {
    DetailedStage generate(Request request);

    record Request(UUID scenarioPackageId, String stageId, long backboneRevision, List<ScenarioSourceReference> evidence) {
        public Request { evidence = List.copyOf(evidence); }
    }
}
