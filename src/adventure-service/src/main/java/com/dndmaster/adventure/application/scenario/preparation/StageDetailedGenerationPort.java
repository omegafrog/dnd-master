package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;

@FunctionalInterface
public interface StageDetailedGenerationPort {
    DetailedStage generate(Request request);

    record Request(String stageId, long backboneRevision, List<ScenarioSourceReference> evidence) {
        public Request { evidence = List.copyOf(evidence); }
    }
}
