package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface StageBackboneGenerationPort {
    StageBackbone generate(Request request);

    record Request(UUID scenarioPackageId, List<ScenarioSourceReference> evidence) {
        public Request { evidence = List.copyOf(evidence); }
    }
}
