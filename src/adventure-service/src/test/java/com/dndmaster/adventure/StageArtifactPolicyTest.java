package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
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

    @Test
    void generated_stage_artifacts_may_omit_source_grounding() {
        assertDoesNotThrow(() -> detailedWithoutGrounding(List.of(
                new RevelationDefinition("revelation-1", true, List.of(evidence))), List.of()));
        assertDoesNotThrow(() -> new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the missing heir", "Learn what happened", List.of())), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeBindingFixture().partialReference());

        var binding = new RuntimeBindingFixture().withReference();
        var updated = binding.withActiveSourceContext(binding.playabilityReport(), binding.activeSourceContext());
        assertEquals(binding.stageBackboneRevision(), updated.stageBackboneRevision());
        assertEquals(binding.currentStageId(), updated.currentStageId());
        assertEquals(binding.detailedStageRevision(), updated.detailedStageRevision());
        var changedPackage = binding.withNewPackage(UUID.randomUUID(), 2, binding.playabilityReport(), binding.activeSourceContext());
        assertNull(changedPackage.stageBackboneRevision());
        assertNull(changedPackage.currentStageId());
        assertNull(changedPackage.detailedStageRevision());
    }

    private static final class RuntimeBindingFixture {
        private final RuntimeBinding binding;

        private RuntimeBindingFixture() {
            binding = RuntimeBinding.create(new com.dndmaster.adventure.domain.adventure.AdventureId(UUID.randomUUID()),
                    new com.dndmaster.adventure.domain.adventure.OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1,
                    List.of(UUID.randomUUID()), List.of(new com.dndmaster.adventure.domain.adventure.AdventurePartyMember(
                            new com.dndmaster.adventure.domain.adventure.CharacterSheetId(UUID.randomUUID()),
                            com.dndmaster.adventure.domain.adventure.ControlMode.DIRECT, false, false, false, false, false, false)),
                    "engine", List.of(), new com.dndmaster.adventure.domain.adventure.PlayabilityReport(
                            com.dndmaster.adventure.domain.adventure.PlayabilityStatus.PLAYABLE, List.of(), List.of(), List.of(), List.of()), null)
                    .withStageReference(1L, "stage-1", 1L);
        }

        private RuntimeBinding withReference() { return binding; }
        private RuntimeBinding partialReference() { return binding.withStageReference(1L, null, 1L); }
    }

    private DetailedStage detailed(List<RevelationDefinition> revelations, List<SituationDefinition> situations) {
        return detailed(revelations, situations, List.of(evidence));
    }

    private DetailedStage detailedWithoutGrounding(List<RevelationDefinition> revelations, List<SituationDefinition> situations) {
        return detailed(revelations, situations, List.of());
    }

    private DetailedStage detailed(List<RevelationDefinition> revelations, List<SituationDefinition> situations,
            List<ScenarioSourceReference> sourceRefs) {
        return new DetailedStage(packageId, 1, "stage-1", 1, "Find the missing heir", revelations,
                new ThreatDefinition("The hunters are closing in", List.of(evidence)),
                new PressureDefinition("The search becomes dangerous", List.of(evidence)),
                new FunnelDefinition("The heir's location is known", List.of("revelation-1"), List.of(evidence)),
                situations, List.of(), sourceRefs);
    }
}
