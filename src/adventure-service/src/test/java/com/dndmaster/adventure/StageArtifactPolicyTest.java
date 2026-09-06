package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageArtifactPolicyTest {
    private final UUID packageId = UUID.randomUUID();
    private final ScenarioSourceReference evidence = new ScenarioSourceReference(
            new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1:span:1");

    @Test
    void detailedStageRequiresGroundingFunnelAndIntentfulSituations() {
        assertThrows(IllegalArgumentException.class, () -> detailed(List.of(), List.of(
                new SituationDefinition("situation-1", List.of(), List.of(evidence)))));
        assertThrows(IllegalArgumentException.class, () -> detailed(List.of(
                new RevelationDefinition("revelation-1", true, List.of(evidence))), List.of(
                new SituationDefinition("situation-1", List.of(), List.of(evidence)))));
        assertThrows(IllegalArgumentException.class, () -> detailed(List.of(
                new RevelationDefinition("revelation-1", true, List.of(evidence))), List.of(
                new SituationDefinition("situation-1", List.of(StageIntent.REVELATION), List.of()))));
    }

    @Test
    void stageArtifactsKeepPackageAndRevisionReferencesAndRejectStaleWrites() {
        StageBackbone backbone = new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the missing heir", "Learn what happened", List.of(evidence))),
                List.of(evidence));
        assertEquals(packageId, backbone.scenarioPackageId());
        assertEquals(1, backbone.revision());

        StageArtifactRepository repository = new StageArtifactRepository.InMemory();
        repository.saveBackbone(backbone, 0);
        assertThrows(IllegalStateException.class, () -> repository.saveBackbone(
                new StageBackbone(packageId, 2, backbone.stages(), backbone.sourceRefs()), 0));
        assertEquals(backbone, repository.findBackbone(packageId, 1).orElseThrow());
    }

    private DetailedStage detailed(List<RevelationDefinition> revelations, List<SituationDefinition> situations) {
        return new DetailedStage(packageId, 1, "stage-1", 1, "Find the missing heir", revelations,
                new ThreatDefinition("The hunters are closing in", List.of(evidence)),
                new PressureDefinition("The search becomes dangerous", List.of(evidence)),
                new FunnelDefinition("The heir's location is known", List.of("revelation-1"), List.of(evidence)),
                situations, List.of(evidence));
    }
}
