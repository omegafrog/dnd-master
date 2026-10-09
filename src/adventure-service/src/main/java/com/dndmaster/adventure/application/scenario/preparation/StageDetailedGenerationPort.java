package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;

@FunctionalInterface
public interface StageDetailedGenerationPort {
    DetailedStage generate(Request request);

    record Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
            long backboneRevision, List<ScenarioSourceReference> evidence, List<RequiredSituation> requiredSituations,
            boolean openingSituationRequired, List<String> alreadyAssignedRequiredSituationIds) {
        public Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
                long backboneRevision, List<ScenarioSourceReference> evidence) {
            this(adventureId, sessionId, scenarioPackageId, stageId, backboneRevision, evidence, List.of(), false, List.of());
        }
        public Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
                long backboneRevision, List<ScenarioSourceReference> evidence, List<RequiredSituation> requiredSituations) {
            this(adventureId, sessionId, scenarioPackageId, stageId, backboneRevision, evidence, requiredSituations, false, List.of());
        }
        public Request(AdventureId adventureId, SessionId sessionId, UUID scenarioPackageId, String stageId,
                long backboneRevision, List<ScenarioSourceReference> evidence, List<RequiredSituation> requiredSituations,
                boolean openingSituationRequired) {
            this(adventureId, sessionId, scenarioPackageId, stageId, backboneRevision, evidence, requiredSituations,
                    openingSituationRequired, List.of());
        }
        public Request { evidence = List.copyOf(evidence); requiredSituations = List.copyOf(requiredSituations);
            alreadyAssignedRequiredSituationIds = List.copyOf(alreadyAssignedRequiredSituationIds); }
    }
}
