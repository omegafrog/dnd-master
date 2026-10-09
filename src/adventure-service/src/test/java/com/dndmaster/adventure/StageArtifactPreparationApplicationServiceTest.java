package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.preparation.StageArtifactPreparationApplicationService;
import com.dndmaster.adventure.application.scenario.preparation.StageBackboneGenerationPort;
import com.dndmaster.adventure.application.scenario.preparation.StageDetailedGenerationPort;
import com.dndmaster.adventure.application.scenario.preparation.StorybookEvidenceLookupPort;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.DetailedStage;
import com.dndmaster.adventure.domain.scenario.FunnelDefinition;
import com.dndmaster.adventure.domain.scenario.PressureDefinition;
import com.dndmaster.adventure.domain.scenario.RevelationDefinition;
import com.dndmaster.adventure.domain.scenario.RequiredSituation;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.SituationDefinition;
import com.dndmaster.adventure.domain.scenario.StageArtifactRepository;
import com.dndmaster.adventure.domain.scenario.StageBackbone;
import com.dndmaster.adventure.domain.scenario.StageBackboneEntry;
import com.dndmaster.adventure.domain.scenario.StageIntent;
import com.dndmaster.adventure.domain.scenario.SituationProvenance;
import com.dndmaster.adventure.domain.scenario.ThreatDefinition;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import org.junit.jupiter.api.Test;

class StageArtifactPreparationApplicationServiceTest {
    private final UUID packageId = UUID.randomUUID();
    private final AdventureId adventureId = AdventureId.generate();
    private final SessionId sessionId = SessionId.generate();
    private final ScenarioSourceReference evidence = new ScenarioSourceReference(
            new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1:span:1");

    @Test
    void creates_backbone_materializes_only_first_stage_and_requires_opening_situation() {
        var backbone = backbone();
        var first = detailed(true);
        var later = secondDetailed();
        var service = new StageArtifactPreparationApplicationService(
                new StageArtifactRepository.InMemory(), request -> List.of(evidence),
                request -> backbone, request -> request.stageId().equals("stage-1") ? first : later);

        var result = service.prepare(adventureId, sessionId, packageId);

        assertEquals(backbone, result.backbone());
        assertEquals(first, result.currentStage());
        assertEquals(2, result.backbone().stages().size());
        assertEquals("opening-situation", result.openingSituation().situationId());
    }

    @Test
    void prepares_and_persists_every_stage_before_returning() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        var first = detailed(true);
        var second = new DetailedStage(packageId, 1, "stage-2", 1, "Face the threat",
                List.of(new RevelationDefinition("truth-2", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth-2"), List.of(evidence)),
                List.of(new SituationDefinition("second-situation", List.of(StageIntent.REVELATION),
                        List.of("truth-2"), List.of(), List.of(), List.of(), List.of(), false, List.of(evidence))),
                List.of(), List.of(evidence));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone,
                request -> request.stageId().equals("stage-1") ? first : second);

        service.prepare(adventureId, sessionId, packageId);

        assertEquals(second, repository.findDetailedStage(adventureId, packageId, "stage-2", 1, 1).orElseThrow());
    }

    @Test
    void passes_previously_assigned_required_ids_to_each_later_stage_without_changing_reviewed_list() {
        var repository = new StageArtifactRepository.InMemory();
        var required = List.of(
                new RequiredSituation("opening", "The bell disappears", List.of(), List.of(evidence)),
                new RequiredSituation("tower", "The bell is found", List.of("opening"), List.of(evidence)));
        var backbone = new StageBackbone(packageId, 1, backbone().stages(), List.of(evidence), required);
        var first = detailedWithSituations(List.of(extractedRequired("opening", List.of()),
                extractedCandidate("candidate-a"), extractedCandidate("candidate-b")));
        var second = new DetailedStage(packageId, 1, "stage-2", 1, "Face the threat",
                List.of(new RevelationDefinition("truth-2", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth-2"), List.of(evidence)),
                List.of(new SituationDefinition("tower", List.of(StageIntent.REVELATION), List.of("truth-2"),
                                List.of(), List.of(), List.of(), List.of(), false, true, List.of("opening"),
                                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)),
                        new SituationDefinition("candidate-c", List.of(StageIntent.REVELATION), List.of("truth-2"),
                                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                                SituationProvenance.GM_SUPPLEMENTED, null, List.of()),
                        new SituationDefinition("candidate-d", List.of(StageIntent.REVELATION), List.of("truth-2"),
                                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                                SituationProvenance.GM_SUPPLEMENTED, null, List.of())),
                List.of(), List.of(evidence));
        var requests = new java.util.ArrayList<StageDetailedGenerationPort.Request>();
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone, request -> {
                    requests.add(request);
                    return request.stageId().equals("stage-1") ? first : second;
                });

        service.prepare(adventureId, sessionId, packageId);

        assertEquals(List.of(), requests.get(0).alreadyAssignedRequiredSituationIds());
        assertEquals(List.of("opening"), requests.get(1).alreadyAssignedRequiredSituationIds());
        assertEquals(required, requests.get(0).requiredSituations());
        assertEquals(required, requests.get(1).requiredSituations());
    }

    @Test
    void rejects_cyclic_prerequisites_before_publishing_any_initial_artifact() {
        var repository = new StageArtifactRepository.InMemory();
        var first = new DetailedStage(packageId, 1, "stage-1", 1, "Find the heir",
                List.of(new RevelationDefinition("truth", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth"), List.of(evidence)),
                List.of(
                        new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                                List.of(), List.of(), List.of(), List.of(), true, true, List.of("candidate"),
                                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)),
                        new SituationDefinition("candidate", List.of(StageIntent.REVELATION), List.of("truth"),
                                List.of(), List.of(), List.of(), List.of(), false, false, List.of("opening"),
                                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence))),
                List.of(), List.of(evidence));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone(),
                request -> request.stageId().equals("stage-1") ? first : secondDetailed());

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
        assertEquals(0, repository.findBackbone(adventureId, packageId, 1).stream().count());
    }

    @Test
    void rejects_reviewed_prerequisite_in_a_later_stage_before_publication() {
        var repository = new StageArtifactRepository.InMemory();
        var required = List.of(
                new RequiredSituation("opening", "The bell disappears", List.of(), List.of(evidence)),
                new RequiredSituation("dependent", "The bell is found", List.of("prerequisite"), List.of(evidence)),
                new RequiredSituation("prerequisite", "The tower is opened", List.of(), List.of(evidence)));
        var backbone = new StageBackbone(packageId, 1, backbone().stages(), List.of(evidence), required);
        var first = detailedWithSituations(List.of(extractedRequired("opening", List.of()),
                extractedRequired("dependent", List.of("prerequisite")), extractedCandidate("candidate-a"),
                extractedCandidate("candidate-b")));
        var later = new DetailedStage(packageId, 1, "stage-2", 1, "Face the threat",
                List.of(new RevelationDefinition("truth-2", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth-2"), List.of(evidence)),
                List.of(new SituationDefinition("prerequisite", List.of(StageIntent.REVELATION), List.of("truth-2"),
                                List.of(), List.of(), List.of(), List.of(), false, true, List.of(),
                                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)),
                        new SituationDefinition("candidate-c", List.of(StageIntent.REVELATION), List.of("truth-2"),
                                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence))),
                List.of(), List.of(evidence));
        var service = new StageArtifactPreparationApplicationService(repository, request -> List.of(evidence),
                request -> backbone, request -> request.stageId().equals("stage-1") ? first : later);

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
        assertEquals(0, repository.findBackbone(adventureId, packageId, 1).stream().count());
    }

    @Test
    void allows_reviewed_prerequisite_and_dependent_in_the_same_stage() {
        var repository = new StageArtifactRepository.InMemory();
        var required = List.of(
                new RequiredSituation("opening", "The bell disappears", List.of(), List.of(evidence)),
                new RequiredSituation("prerequisite", "The tower is opened", List.of("opening"), List.of(evidence)));
        var backbone = new StageBackbone(packageId, 1, singleStageBackbone().stages(), List.of(evidence), required);
        var stage = detailedWithSituations(List.of(extractedRequired("opening", List.of()),
                extractedRequired("prerequisite", List.of("opening")), extractedCandidate("candidate-a"),
                extractedCandidate("candidate-b")));
        var service = new StageArtifactPreparationApplicationService(repository, request -> List.of(evidence),
                request -> backbone, request -> stage);

        assertEquals(stage, service.prepare(adventureId, sessionId, packageId).currentStage());
    }

    @Test
    void reports_only_safe_origin_fields_when_initial_situation_ids_still_collide() {
        var repository = new StageArtifactRepository.InMemory();
        var first = detailedWithSituations(List.of(
                extractedRequired("opening-situation", List.of()),
                supplementalCandidate("repeated-candidate"), supplementalCandidate("candidate-b")));
        var second = new DetailedStage(packageId, 1, "stage-2", 1, "Face the threat",
                List.of(new RevelationDefinition("truth-2", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth-2"), List.of(evidence)),
                List.of(new SituationDefinition("repeated-candidate", List.of(StageIntent.REVELATION), List.of("truth-2"),
                        List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                        SituationProvenance.GM_SUPPLEMENTED, null, List.of())), List.of(), List.of(evidence));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone(),
                request -> request.stageId().equals("stage-1") ? first : second);

        var failure = assertThrows(IllegalStateException.class,
                () -> service.prepare(adventureId, sessionId, packageId));

        assertTrue(failure.getMessage().contains("previousStageId=stage-1"));
        assertTrue(failure.getMessage().contains("previousProvenance=GM_SUPPLEMENTED"));
        assertTrue(failure.getMessage().contains("previousRequired=false"));
        assertTrue(failure.getMessage().contains("currentStageId=stage-2"));
        assertTrue(failure.getMessage().contains("currentProvenance=GM_SUPPLEMENTED"));
        assertTrue(failure.getMessage().contains("currentRequired=false"));
        assertFalse(failure.getMessage().contains("repeated-candidate"));
        assertEquals(0, repository.findBackbone(adventureId, packageId, 1).stream().count());
    }

    @Test
    void keeps_required_and_supplemented_candidates_distinct_and_accepts_only_provided_maps() {
        UUID providedMapId = UUID.randomUUID();
        UUID outsideMapId = UUID.randomUUID();
        var opening = new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), true, true, List.of(),
                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence));
        var candidate = new SituationDefinition("candidate", List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of("opening"),
                SituationProvenance.GM_SUPPLEMENTED, providedMapId, List.of());
        var maplessCandidate = new SituationDefinition("mapless-candidate", List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of("opening"),
                SituationProvenance.GM_SUPPLEMENTED, null, List.of());
        var stage = detailedWithSituations(List.of(opening, candidate, maplessCandidate));
        var lookup = lookupWithMap(providedMapId);
        var repository = new StageArtifactRepository.InMemory();
        var service = new StageArtifactPreparationApplicationService(repository, lookup,
                request -> singleStageBackbone(), request -> stage);

        var result = service.prepare(adventureId, sessionId, packageId);

        assertEquals(3, result.currentStage().situations().size());
        assertEquals(true, result.currentStage().situations().get(0).required());
        assertEquals(SituationProvenance.SOURCE_EXTRACTED, result.currentStage().situations().get(0).provenance());
        assertEquals(false, result.currentStage().situations().get(1).required());
        assertEquals(SituationProvenance.GM_SUPPLEMENTED, result.currentStage().situations().get(1).provenance());
        assertEquals(providedMapId, result.currentStage().situations().get(1).mapDefinitionId());
        assertEquals(null, result.currentStage().situations().get(2).mapDefinitionId());

        var invalidRepository = new StageArtifactRepository.InMemory();
        var invalid = new StageArtifactPreparationApplicationService(invalidRepository, lookup,
                request -> singleStageBackbone(), request -> detailedWithSituations(List.of(opening,
                        new SituationDefinition("outside-map", List.of(StageIntent.REVELATION), List.of("truth"),
                                List.of(), List.of(), List.of(), List.of(), false, false, List.of("opening"),
                                SituationProvenance.GM_SUPPLEMENTED, outsideMapId, List.of()))));
        assertThrows(IllegalStateException.class, () -> invalid.prepare(adventureId, sessionId, packageId));
        assertEquals(0, invalidRepository.findBackbone(adventureId, packageId, 1).stream().count());
    }

    @Test
    void continues_with_gm_supplement_when_story_evidence_does_not_ground_generated_details() {
        var backbone = new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Begin the adventure", "Find a direction", List.of())), List.of());
        var stage = new DetailedStage(packageId, 1, "stage-1", 1, "Begin the adventure",
                List.of(new RevelationDefinition("truth", false, List.of())),
                new ThreatDefinition("A local mystery", List.of()),
                new PressureDefinition("The situation develops", List.of()),
                new FunnelDefinition("Choose what to investigate", List.of(), List.of()),
                List.of(new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                        List.of(), List.of(), List.of(), List.of(), true, true, List.of(),
                        SituationProvenance.GM_SUPPLEMENTED, null, List.of()),
                        supplementalCandidate("candidate-a"), supplementalCandidate("candidate-b")), List.of(), List.of());
        var service = new StageArtifactPreparationApplicationService(new StageArtifactRepository.InMemory(),
                request -> List.of(evidence), request -> backbone, request -> stage);

        var result = service.prepare(adventureId, sessionId, packageId);

        assertEquals(SituationProvenance.GM_SUPPLEMENTED, result.openingSituation().provenance());
        assertEquals(List.of(), result.openingSituation().sourceRefs());
    }

    @Test
    void rejects_gm_supplement_that_claims_a_source_reference() {
        var repository = new StageArtifactRepository.InMemory();
        var claimedSource = new SituationDefinition("candidate-a", List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                SituationProvenance.GM_SUPPLEMENTED, null, List.of(evidence));
        var stage = detailedWithSituations(List.of(
                new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                        List.of(), List.of(), List.of(), List.of(), true, true, List.of(),
                        SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)),
                claimedSource, supplementalCandidate("candidate-b")));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> singleStageBackbone(), request -> stage);

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
        assertEquals(0, repository.findBackbone(adventureId, packageId, 1).stream().count());
    }

    @Test
    void requires_multiple_intermediate_candidates_without_a_fixed_maximum() {
        for (int candidateCount = 0; candidateCount <= 3; candidateCount++) {
            var repository = new StageArtifactRepository.InMemory();
            var situations = new java.util.ArrayList<SituationDefinition>();
            situations.add(new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                    List.of(), List.of(), List.of(), List.of(), true, true, List.of(),
                    SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)));
            situations.add(new SituationDefinition("required-beat-a", List.of(StageIntent.REVELATION), List.of("truth"),
                    List.of(), List.of(), List.of(), List.of(), false, true, List.of(),
                    SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)));
            situations.add(new SituationDefinition("required-beat-b", List.of(StageIntent.REVELATION), List.of("truth"),
                    List.of(), List.of(), List.of(), List.of(), false, true, List.of(),
                    SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)));
            for (int index = 0; index < candidateCount; index++) {
                situations.add(new SituationDefinition("candidate-" + index, List.of(StageIntent.REVELATION), List.of("truth"),
                        List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                        SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)));
            }
            var service = new StageArtifactPreparationApplicationService(repository,
                    request -> List.of(evidence), request -> singleStageBackbone(), request -> detailedWithSituations(situations));

            if (candidateCount < 2) {
                assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
                assertEquals(0, repository.findBackbone(adventureId, packageId, 1).stream().count());
            } else {
                var result = service.prepare(adventureId, sessionId, packageId);
                assertEquals(candidateCount + 3, result.currentStage().situations().size());
            }
        }
    }

    @Test
    void rejects_initial_set_without_a_required_situation() {
        var repository = new StageArtifactRepository.InMemory();
        var stage = detailedWithSituations(List.of(
                new SituationDefinition("opening", List.of(StageIntent.REVELATION), List.of("truth"),
                        List.of(), List.of(), List.of(), List.of(), true, false, List.of(),
                        SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence)),
                extractedCandidate("candidate-a"), extractedCandidate("candidate-b")));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> singleStageBackbone(), request -> stage);

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
        assertEquals(0, repository.findBackbone(adventureId, packageId, 1).stream().count());
    }

    @Test
    void rejects_missing_reviewed_situation_and_explicit_prerequisite_before_atomic_publication() {
        var extraSource = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:2:span:1");
        var required = List.of(
                new RequiredSituation("opening", "The bell disappears", List.of(), List.of(evidence)),
                new RequiredSituation("tower", "The bell is found", List.of("opening"), List.of(evidence)));
        var backbone = new StageBackbone(packageId, 1, singleStageBackbone().stages(), List.of(evidence), required);
        for (List<SituationDefinition> situations : List.of(
                List.of(extractedRequired("opening", List.of()), extractedCandidate("candidate-a"),
                        extractedCandidate("candidate-b")),
                List.of(extractedRequired("opening", List.of()), extractedRequired("tower", List.of()),
                        extractedCandidate("candidate-a"), extractedCandidate("candidate-b")),
                List.of(extractedRequired("opening", List.of()), extractedRequired("tower", List.of("opening", "candidate-a")),
                        extractedCandidate("candidate-a"), extractedCandidate("candidate-b")),
                List.of(extractedRequired("opening", List.of()),
                        new SituationDefinition("tower", List.of(StageIntent.REVELATION), List.of("truth"),
                                List.of(), List.of(), List.of(), List.of(), false, true, List.of("opening"),
                                SituationProvenance.GM_SUPPLEMENTED, null, List.of()),
                        extractedCandidate("candidate-a"), extractedCandidate("candidate-b")),
                List.of(extractedRequired("opening", List.of()),
                        new SituationDefinition("tower", List.of(StageIntent.REVELATION), List.of("truth"),
                                List.of(), List.of(), List.of(), List.of(), false, true, List.of("opening"),
                                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence, extraSource)),
                        extractedCandidate("candidate-a"), extractedCandidate("candidate-b")))) {
            var repository = new StageArtifactRepository.InMemory();
            var service = new StageArtifactPreparationApplicationService(repository,
                    ignored -> List.of(evidence, extraSource), ignored -> backbone, ignored -> detailedWithSituations(situations));
            assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
            assertEquals(0, repository.findBackbone(adventureId, packageId, 1).stream().count());
        }
        var repository = new StageArtifactRepository.InMemory();
        var complete = detailedWithSituations(List.of(extractedRequired("opening", List.of()),
                extractedRequired("tower", List.of("opening")), extractedCandidate("candidate-a"),
                extractedCandidate("candidate-b")));
        var service = new StageArtifactPreparationApplicationService(repository,
                ignored -> List.of(evidence), ignored -> backbone, ignored -> complete);
        service.prepare(adventureId, sessionId, packageId);
        assertEquals(required, repository.findBackbone(adventureId, packageId, 1).orElseThrow().requiredSituations());
        assertEquals(complete, service.prepare(adventureId, sessionId, packageId).currentStage());
    }

    @Test
    void preserves_existing_artifacts_when_detailed_generation_fails() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        repository.saveBackbone(adventureId, backbone, 0);
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone,
                request -> { throw new IllegalStateException("generation failed"); });

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
        assertEquals(backbone, repository.findBackbone(adventureId, packageId, 1).orElseThrow());
        assertEquals(0, repository.findDetailedStage(adventureId, packageId, "stage-1", 1, 1).stream().count());
    }

    @Test
    void does_not_leave_backbone_when_initial_detailed_stage_fails() {
        var repository = new FailingInitialRepository();
        var backbone = backbone();
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone,
                request -> {
                    if (request.stageId().equals("stage-1")) return detailed(true);
                    throw new IllegalStateException("generation failed");
                });

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
        assertEquals(0, repository.backboneCount);
    }

    @Test
    void rejects_artifacts_grounded_in_evidence_not_returned_by_lookup() {
        ScenarioSourceReference foreignEvidence = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 1, "page:9:span:9");
        StageBackbone foreignBackbone = new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the heir", "Learn the truth", List.of(foreignEvidence))), List.of(foreignEvidence));
        var service = new StageArtifactPreparationApplicationService(new StageArtifactRepository.InMemory(),
                request -> List.of(evidence), request -> foreignBackbone, request -> detailed(true));

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
    }

    @Test
    void rejects_opening_situation_without_grounding_when_storybook_evidence_exists() {
        var service = new StageArtifactPreparationApplicationService(new StageArtifactRepository.InMemory(),
                request -> List.of(evidence), request -> backbone(), request -> detailedWithOpening("opening-situation", List.of()));

        assertThrows(IllegalStateException.class, () -> service.prepare(adventureId, sessionId, packageId));
    }

    @Test
    void uses_persisted_detailed_stage_as_execution_snapshot() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        var persisted = detailedWithOpening("persisted-opening", List.of(evidence));
        repository.saveInitialArtifacts(adventureId, backbone, List.of(persisted, secondDetailed()));
        var regenerated = detailedWithOpening("regenerated-opening", List.of(evidence));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone, request -> regenerated);

        assertEquals("persisted-opening", service.prepare(adventureId, sessionId, packageId).openingSituation().situationId());
    }

    @Test
    void reuses_complete_saved_set_when_original_source_temporarily_cannot_be_read() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        repository.saveInitialArtifacts(adventureId, backbone, List.of(detailed(true), secondDetailed()));
        var unavailable = new StageArtifactPreparationApplicationService(repository,
                ignored -> { throw new IllegalStateException("source preview unavailable"); },
                ignored -> { throw new AssertionError("saved set must not regenerate"); },
                ignored -> { throw new AssertionError("saved stage must not regenerate"); });

        assertEquals(backbone, unavailable.prepare(adventureId, sessionId, packageId).backbone());
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
                        List.of(), List.of(), List.of(), List.of(), true, List.of()),
                        supplementalCandidate("candidate-a"), supplementalCandidate("candidate-b")), List.of(), List.of());
        StorybookEvidenceLookupPort lookup = new StorybookEvidenceLookupPort() {
            @Override public List<ScenarioSourceReference> lookup(UUID id) { return List.of(); }
            @Override public boolean isScenarioBacked(UUID id) { return false; }
        };
        var service = new StageArtifactPreparationApplicationService(new StageArtifactRepository.InMemory(), lookup,
                request -> backbone, request -> stage);

        assertEquals("opening-situation", service.prepare(adventureId, sessionId, packageId).openingSituation().situationId());
    }

    @Test
    void materializes_only_the_immediate_next_stage_and_validates_its_grounding() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        repository.saveInitialArtifacts(adventureId, backbone, List.of(detailed(true), secondDetailed()));
        DetailedStage next = new DetailedStage(packageId, 1, "stage-2", 1, "Face the threat",
                List.of(new RevelationDefinition("truth-2", true, List.of(evidence))),
                new ThreatDefinition("The hunters arrive", List.of(evidence)),
                new PressureDefinition("The danger rises", List.of(evidence)),
                new FunnelDefinition("Reach the confrontation", List.of("truth-2"), List.of(evidence)),
                List.of(new SituationDefinition("stage-2-situation", List.of(StageIntent.REVELATION), List.of("truth-2"),
                        List.of(), List.of(), List.of(), List.of(), false, List.of(evidence))), List.of(), List.of(evidence));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone, request -> next);

        assertEquals(secondDetailed(), service.prepareNext(adventureId, sessionId, packageId, backbone, "stage-1"));
        assertEquals(secondDetailed(), repository.findDetailedStage(adventureId, packageId, "stage-2", 1, 1).orElseThrow());
    }

    @Test
    void does_not_persist_next_stage_when_grounding_validation_fails() {
        var repository = new StageArtifactRepository.InMemory();
        var backbone = backbone();
        repository.saveInitialArtifacts(adventureId, backbone, List.of(detailed(true), secondDetailed()));
        ScenarioSourceReference foreign = new ScenarioSourceReference(new KnowledgeDocumentId(UUID.randomUUID()), 2, "page:9:span:9");
        DetailedStage invalid = new DetailedStage(packageId, 1, "stage-2", 1, "Face the threat",
                List.of(new RevelationDefinition("truth-2", true, List.of(foreign))),
                new ThreatDefinition("The hunters arrive", List.of(foreign)),
                new PressureDefinition("The danger rises", List.of(foreign)),
                new FunnelDefinition("Reach the confrontation", List.of("truth-2"), List.of(foreign)),
                List.of(new SituationDefinition("stage-2-situation", List.of(StageIntent.REVELATION), List.of("truth-2"),
                        List.of(), List.of(), List.of(), List.of(), false, List.of(foreign))), List.of(), List.of(foreign));
        var service = new StageArtifactPreparationApplicationService(repository,
                request -> List.of(evidence), request -> backbone, request -> invalid);

        assertEquals(secondDetailed(), service.prepareNext(adventureId, sessionId, packageId, backbone, "stage-1"));
        assertEquals(secondDetailed(), repository.findDetailedStage(adventureId, packageId, "stage-2", 1, 1).orElseThrow());
    }

    private static final class FailingInitialRepository implements StageArtifactRepository {
        private int backboneCount;
        private final StageArtifactRepository delegate = new StageArtifactRepository.InMemory();
        @Override public void saveInitialArtifacts(AdventureId adventureId, StageBackbone backbone, List<DetailedStage> stages) {
            throw new IllegalStateException("atomic write failed");
        }
        @Override public java.util.Optional<StageBackbone> findBackbone(AdventureId adventureId, UUID id, long revision) { return delegate.findBackbone(adventureId, id, revision); }
        @Override public java.util.Optional<DetailedStage> findDetailedStage(AdventureId adventureId, UUID id, String stage, long backbone, long revision) { return delegate.findDetailedStage(adventureId, id, stage, backbone, revision); }
        @Override public void saveBackbone(AdventureId adventureId, StageBackbone backbone, long expectedRevision) { delegate.saveBackbone(adventureId, backbone, expectedRevision); backboneCount++; }
        @Override public void saveDetailedStage(AdventureId adventureId, DetailedStage stage, long expectedRevision) { delegate.saveDetailedStage(adventureId, stage, expectedRevision); }
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

    private DetailedStage secondDetailed() {
        return new DetailedStage(packageId, 1, "stage-2", 1, "Face the threat",
                List.of(new RevelationDefinition("truth-2", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth-2"), List.of(evidence)),
                List.of(new SituationDefinition("second-situation", List.of(StageIntent.REVELATION),
                        List.of("truth-2"), List.of(), List.of(), List.of(), List.of(), false, List.of(evidence))),
                List.of(), List.of(evidence));
    }

    private DetailedStage detailedWithOpening(String openingId, List<ScenarioSourceReference> openingEvidence) {
        return new DetailedStage(packageId, 1, "stage-1", 1, "Find the heir",
                List.of(new RevelationDefinition("truth", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth"), List.of(evidence)),
                List.of(new SituationDefinition(openingId, List.of(StageIntent.REVELATION),
                        List.of("truth"), List.of(), List.of(), List.of(), List.of(), true, openingEvidence),
                        extractedCandidate("candidate-a"), extractedCandidate("candidate-b")),
                List.of(), List.of(evidence));
    }

    private SituationDefinition extractedCandidate(String id) {
        return new SituationDefinition(id, List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence));
    }

    private SituationDefinition extractedRequired(String id, List<String> prerequisites) {
        return new SituationDefinition(id, List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), id.equals("opening"), true, prerequisites,
                SituationProvenance.SOURCE_EXTRACTED, null, List.of(evidence));
    }

    private SituationDefinition supplementalCandidate(String id) {
        return new SituationDefinition(id, List.of(StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), false, false, List.of(),
                SituationProvenance.GM_SUPPLEMENTED, null, List.of());
    }

    private DetailedStage detailedWithSituations(List<SituationDefinition> situations) {
        return new DetailedStage(packageId, 1, "stage-1", 1, "Find the heir",
                List.of(new RevelationDefinition("truth", true, List.of(evidence))),
                new ThreatDefinition("Hunters close in", List.of(evidence)),
                new PressureDefinition("The search grows dangerous", List.of(evidence)),
                new FunnelDefinition("The heir is located", List.of("truth"), List.of(evidence)),
                situations, List.of(), List.of(evidence));
    }

    private StageBackbone singleStageBackbone() {
        return new StageBackbone(packageId, 1, List.of(
                new StageBackboneEntry("stage-1", 1, "opening", "Find the heir", "Learn the truth", List.of(evidence))),
                List.of(evidence));
    }

    private StorybookEvidenceLookupPort lookupWithMap(UUID mapId) {
        return new StorybookEvidenceLookupPort() {
            @Override public List<ScenarioSourceReference> lookup(UUID ignored) { return List.of(evidence); }
            @Override public java.util.Set<UUID> providedMapIds(UUID ignored) { return java.util.Set.of(mapId); }
        };
    }
}
