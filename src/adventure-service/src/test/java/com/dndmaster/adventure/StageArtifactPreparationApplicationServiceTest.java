package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService;
import com.dndmaster.adventure.application.scenario.preparation.StageBackboneGenerationPort;
import com.dndmaster.adventure.application.scenario.preparation.StageDetailedGenerationPort;
import com.dndmaster.adventure.application.scenario.preparation.StorybookEvidenceLookupPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageArtifactPreparationApplicationServiceTest {
    private final UUID packageId = UUID.randomUUID();
    private final ScenarioSourceReference evidence = new ScenarioSourceReference(
            new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1:span:1");

    @Test
    void creates_backbone_materializes_only_first_stage_and_requires_opening_situation() {
        var backbone = backbone();
        var first = detailed(true);
        var later = detailed(false);
        var service = new StageArtifactPreparationApplicationService(
                new StageArtifactRepository.InMemory(), request -> List.of(evidence),
                request -> backbone, request -> request.stageId().equals("stage-1") ? first : later);

        var result = service.prepare(packageId);

        assertEquals(backbone, result.backbone());
        assertEquals(first, result.currentStage());
        assertEquals(2, result.backbone().stages().size());
        assertEquals("opening-situation", result.openingSituation().situationId());
    }

    @Test
    void preserves_existing_artifacts_when_detailed_generation_fails() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        repository.saveBackbone(backbone, 0);
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone,
                request -> { throw new IllegalStateException("generation failed"); });

        assertThrows(IllegalStateException.class, () -> service.prepare(packageId));
        assertEquals(backbone, repository.findBackbone(packageId, 1).orElseThrow());
        assertEquals(0, repository.findDetailedStage(packageId, "stage-1", 1, 1).stream().count());
    }

    @Test
    void does_not_leave_backbone_when_initial_detailed_stage_fails() {
        var repository = new FailingInitialRepository();
        var backbone = backbone();
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone,
                request -> { throw new IllegalStateException("generation failed"); });

        assertThrows(IllegalStateException.class, () -> service.prepare(packageId));
        assertEquals(0, repository.backboneCount);
    }

    @Test
    void rejects_artifacts_grounded_in_evidence_not_returned_by_lookup() {
        ScenarioSourceReference foreignEvidence = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:9:span:9");
        StageBackbone foreignBackbone = new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the heir", "Learn the truth", List.of(foreignEvidence))), List.of(foreignEvidence));
        var service = new StageArtifactPreparationApplicationService(new StageArtifactRepository.InMemory(),
                request -> List.of(evidence), request -> foreignBackbone, request -> detailed(true));

        assertThrows(IllegalStateException.class, () -> service.prepare(packageId));
    }

    @Test
    void rejects_opening_situation_without_grounding_when_storybook_evidence_exists() {
        var service = new StageArtifactPreparationApplicationService(new StageArtifactRepository.InMemory(),
                request -> List.of(evidence), request -> backbone(), request -> detailedWithOpening("opening-situation", List.of()));

        assertThrows(IllegalStateException.class, () -> service.prepare(packageId));
    }

    @Test
    void uses_persisted_detailed_stage_as_execution_snapshot() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        var persisted = detailedWithOpening("persisted-opening", List.of(evidence));
        repository.saveInitialArtifacts(backbone, persisted);
        var regenerated = detailedWithOpening("regenerated-opening", List.of(evidence));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone, request -> regenerated);

        assertEquals("persisted-opening", service.prepare(packageId).openingSituation().situationId());
    }

    @Test
    void allows_rulebook_only_generation_without_storybook_evidence() {
        StageBackbone backbone = new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the heir", "Learn the truth", List.of())), List.of());
        DetailedStage stage = new DetailedStage(packageId, 1, "stage-1", 1, "Find the heir",
                List.of(new RevelationDefinition("truth", true, List.of())),
                new ThreatDefinition("Hunters close in", List.of()),
                new PressureDefinition("The search grows dangerous", List.of()),
                new FunnelDefinition("The heir is located", List.of("truth"), List.of()),
                List.of(new SituationDefinition("opening-situation", List.of(StageIntent.REVELATION), List.of("truth"),
                        List.of(), List.of(), List.of(), List.of(), true, List.of())), List.of(), List.of());
        StorybookEvidenceLookupPort lookup = new StorybookEvidenceLookupPort() {
            @Override public List<ScenarioSourceReference> lookup(UUID id) { return List.of(); }
            @Override public boolean isScenarioBacked(UUID id) { return false; }
        };
        var service = new StageArtifactPreparationApplicationService(new StageArtifactRepository.InMemory(), lookup,
                request -> backbone, request -> stage);

        assertEquals("opening-situation", service.prepare(packageId).openingSituation().situationId());
    }

    private static final class FailingInitialRepository implements StageArtifactRepository {
        private int backboneCount;
        private final StageArtifactRepository delegate = new StageArtifactRepository.InMemory();
        @Override public void saveInitialArtifacts(StageBackbone backbone, DetailedStage stage) {
            throw new IllegalStateException("atomic write failed");
        }
        @Override public java.util.Optional<StageBackbone> findBackbone(UUID id, long revision) { return delegate.findBackbone(id, revision); }
        @Override public java.util.Optional<DetailedStage> findDetailedStage(UUID id, String stage, long backbone, long revision) { return delegate.findDetailedStage(id, stage, backbone, revision); }
        @Override public void saveBackbone(StageBackbone backbone, long expectedRevision) { delegate.saveBackbone(backbone, expectedRevision); backboneCount++; }
        @Override public void saveDetailedStage(DetailedStage stage, long expectedRevision) { delegate.saveDetailedStage(stage, expectedRevision); }
    }

    private StageBackbone backbone() {
        return new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the heir", "Learn the truth", List.of(evidence)),
                new StageBackboneEntry("stage-2", 2, "confrontation", "Face the threat", "Reach the lair", List.of(evidence))),
                List.of(evidence));
    }

    private DetailedStage detailed(boolean opening) {
        return detailedWithOpening("opening-situation", opening ? List.of(evidence) : List.of(evidence));
    }

    private DetailedStage detailedWithOpening(String openingId, List<ScenarioSourceReference> openingEvidence) {
        return new DetailedStage(packageId, 1, "stage-1", 1, "Find the heir",
                List.of(new RevelationDefinition("truth", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth"), List.of(evidence)),
                List.of(new SituationDefinition(openingId, List.of(StageIntent.REVELATION),
                        List.of("truth"), List.of(), List.of(), List.of(), List.of(), true, openingEvidence)),
                List.of(), List.of(evidence));
    }
}
