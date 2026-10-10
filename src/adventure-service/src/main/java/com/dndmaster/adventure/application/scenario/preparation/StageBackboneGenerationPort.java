package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;

@FunctionalInterface
public interface StageBackboneGenerationPort {
    StageBackbone generate(Request request);

    record Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId,
            List<ScenarioSourceReference> evidence, List<StorySourcePreviewPort.Passage> storyPassages) {
        public Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId,
                List<ScenarioSourceReference> evidence) {
            this(adventureId, sessionId, scenarioPackageId, evidence, List.of());
        }
        public Request {
            evidence = List.copyOf(evidence);
            storyPassages = List.copyOf(storyPassages);
        }
    }
}
