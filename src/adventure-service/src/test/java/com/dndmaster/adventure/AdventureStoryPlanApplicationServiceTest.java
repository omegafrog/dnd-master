package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanCandidateValidationException;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation.Repairability;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanApplicationServiceTest {
    @Test
    void refuses_normal_regeneration_after_adventure_started() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        session.start(AdventureId.generate(), UUID.randomUUID());
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));

        var service = new AdventureStoryPlanApplicationService(plans, sessions);

        var failure = assertThrows(IllegalStateException.class, () -> service.generate(session.id(), session.ownerPlayerId()));
        assertEquals("story plan generation is not allowed after adventure start; use future-stage revision", failure.getMessage());
        verify(plans, never()).save(any());
    }

    @Test
    void refuses_to_generate_or_persist_ready_plan_from_partial_scenario_package() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var packages = mock(com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        when(packages.findById(session.scenarioPackageId())).thenReturn(Optional.of(ScenarioPackage.publish(
                new ScenarioBundleId(session.scenarioPackageId()), 1, "partial", java.util.List.of(), java.util.List.of(),
                new ScenarioCompilationReport(ResolutionStatus.PARTIAL, java.util.List.of("incomplete")))));

        var service = new AdventureStoryPlanApplicationService(plans, sessions, packages,
                request -> AdventureStoryPlanGenerationPort.ProjectionCandidate.fromStages(java.util.List.of()));

        assertThrows(IllegalStateException.class, () -> service.generate(session.id(), session.ownerPlayerId()));
        verify(plans, never()).save(any());
    }

    @Test
    void generates_plan_only_for_complete_party_and_captures_party_revision() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 3, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        var service = new AdventureStoryPlanApplicationService(plans, sessions);

        var plan = service.generate(session.id(), session.ownerPlayerId());

        assertEquals(AdventureStoryPlanStatus.READY, plan.status());
        assertEquals(session.version(), plan.partyRevision());
        assertEquals(4, plan.stageCount());
        verify(plans).save(plan);
    }

    @Test
    void rejects_incomplete_party() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 2,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> new AdventureStoryPlanApplicationService(plans, sessions).generate(session.id(), session.ownerPlayerId()));
        verify(plans, never()).save(any());
    }

    @Test
    void forwards_requested_length_and_ending_count_to_generator_and_plan() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        when(generator.generate(any())).thenReturn(AdventureStoryPlanGenerationPort.ProjectionCandidate.fromStages(java.util.List.of(
                new AdventureStoryPlanStage(1, "One", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(2, "Two", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(3, "Three", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(4, "Four", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(5, "Five", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(6, "Six", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(7, "Seven", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")))));

        var configuration = new AdventurePlanConfiguration(2, AdventureLength.LONG);
        var plan = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                .generate(session.id(), session.ownerPlayerId(), configuration);

        var request = org.mockito.ArgumentCaptor.forClass(AdventureStoryPlanGenerationPort.Request.class);
        verify(generator).generate(request.capture());
        verify(generator, never()).generateTacticalScene(any());
        assertEquals(configuration, request.getValue().configuration());
        assertEquals(configuration, plan.configuration());
        assertTrue(plan.stages().stream().allMatch(stage -> stage.tacticalScenePlan().status()
                == TacticalScenePlanStatus.ABSENT));
    }

    @Test
    void rejects_more_than_four_endings() {
        assertThrows(IllegalArgumentException.class, () -> new AdventurePlanConfiguration(5, AdventureLength.STANDARD));
    }

    @Test
    void retries_projection_validation_with_violations_and_publishes_next_ready_candidate() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        var readyStages = java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(position -> new AdventureStoryPlanStage(position, "Stage " + position, "Goal", "Conflict", "Continue",
                        java.util.List.of(), java.util.List.of("ending-a", "ending-b"))).toList();
        when(generator.generate(any())).thenThrow(new AdventureStoryPlanCandidateValidationException(List.of("endingIds must be explicit")))
                .thenReturn(AdventureStoryPlanGenerationPort.ProjectionCandidate.fromStages(readyStages));

        var service = new AdventureStoryPlanApplicationService(plans, sessions, null, generator);
        var progressStages = new java.util.ArrayList<String>();
        var result = service.generate(session.id(), session.ownerPlayerId(), AdventurePlanConfiguration.defaults(),
                (percent, stage) -> progressStages.add(stage));

        assertEquals(AdventureStoryPlanStatus.READY, result.status());
        var requests = org.mockito.ArgumentCaptor.forClass(AdventureStoryPlanGenerationPort.Request.class);
        verify(generator, times(2)).generate(requests.capture());
        assertEquals(java.util.List.of("endingIds must be explicit"), requests.getAllValues().get(1).violations());
        assertTrue(progressStages.contains("계획 검증 실패, 재시도 준비 중 (1/5)"));
        assertTrue(progressStages.contains("모험 개요 재생성 중 (재시도 2/5)"));
        assertEquals("플레이 준비 완료", progressStages.get(progressStages.size() - 1));
        verify(plans).save(result);
    }

    @Test
    void stops_identical_missing_candidate_and_identical_violation_without_five_retries() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        when(generator.generate(any())).thenThrow(new AdventureStoryPlanCandidateValidationException(List.of("hidden trigger outcomes are incomplete")));

        var result = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                .generate(session.id(), session.ownerPlayerId());

        assertEquals(AdventureStoryPlanStatus.BLOCKED, result.status());
        verify(generator, times(2)).generate(any());
        assertTrue(result.failureReason().contains("hidden trigger outcomes are incomplete"));
        verify(plans).save(result);
    }

    @Test
    void supplies_the_failed_projection_itself_to_bounded_repair_and_persists_only_after_full_revalidation() {
        var session = draftSession();
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        String rejected = shortCandidate("bad");
        String repaired = shortCandidate("good");
        var violation = new AdventureStoryPlanProjectionViolation("INVALID_TRANSITION_CONDITION", 1,
                "stages[0].transitionCondition", "bad", "", Repairability.REPAIRABLE,
                "transitionCondition is not usable");
        var clearViolation = new AdventureStoryPlanProjectionViolation("INVALID_CLEAR_CONDITION", 1,
                "stages[0].clearCondition", "bad", "", Repairability.REPAIRABLE,
                "clearCondition is not usable");
        when(generator.generate(any())).thenThrow(new AdventureStoryPlanCandidateValidationException(
                List.of(violation, clearViolation), rejected, true));
        when(generator.repair(any())).thenReturn(new AdventureStoryPlanGenerationPort.ProjectionCandidate(
                repaired, shortStages("good")));

        var result = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                .generate(session.id(), session.ownerPlayerId(), new AdventurePlanConfiguration(2, AdventureLength.SHORT));

        assertEquals(AdventureStoryPlanStatus.READY, result.status());
        var repair = org.mockito.ArgumentCaptor.forClass(AdventureStoryPlanGenerationPort.RepairRequest.class);
        verify(generator).repair(repair.capture());
        assertEquals(rejected, repair.getValue().previousCandidate());
        assertEquals(List.of(violation, clearViolation), repair.getValue().violations());
        verify(plans).save(result);
    }

    @Test
    void enforces_two_repairs_one_regeneration_and_never_exceeds_five_candidate_attempts() {
        var session = draftSession();
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        String rejected = "{\"stages\":[{\"position\":1,\"transitionCondition\":\"bad\"}]}";
        var first = violation("V1", "stages[0].transitionCondition");
        var second = violation("V2", "stages[0].clearCondition");
        var third = violation("V3", "stages[0].failureCondition");
        var generations = new java.util.concurrent.atomic.AtomicInteger();
        var repairs = new java.util.concurrent.atomic.AtomicInteger();
        when(generator.generate(any())).thenAnswer(invocation -> {
            if (generations.incrementAndGet() == 1) throw new AdventureStoryPlanCandidateValidationException(List.of(first), rejected, true);
            throw new AdventureStoryPlanCandidateValidationException(List.of(third), rejected, true);
        });
        when(generator.repair(any())).thenAnswer(invocation -> {
            int attempt = repairs.incrementAndGet();
            throw new AdventureStoryPlanCandidateValidationException(List.of(attempt == 1 ? second : third), rejected, true);
        });

        var result = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                .generate(session.id(), session.ownerPlayerId(), new AdventurePlanConfiguration(2, AdventureLength.SHORT));

        assertEquals(AdventureStoryPlanStatus.BLOCKED, result.status());
        assertEquals(2, repairs.get());
        assertEquals(2, generations.get());
        org.mockito.Mockito.verify(generator, org.mockito.Mockito.atMost(5)).generate(any());
        verify(plans).save(result);
    }

    @Test
    void stops_identical_candidate_and_identical_violation_without_a_futile_second_repair() {
        var session = draftSession();
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        String rejected = "{\"stages\":[{\"position\":1,\"transitionCondition\":\"bad\"}]}";
        var violation = violation("INVALID_TRANSITION_CONDITION", "stages[0].transitionCondition");
        when(generator.generate(any())).thenThrow(new AdventureStoryPlanCandidateValidationException(List.of(violation), rejected, true));
        when(generator.repair(any())).thenThrow(new AdventureStoryPlanCandidateValidationException(List.of(violation), rejected, true));

        var result = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                .generate(session.id(), session.ownerPlayerId(), new AdventurePlanConfiguration(2, AdventureLength.SHORT));

        assertEquals(AdventureStoryPlanStatus.BLOCKED, result.status());
        verify(generator, times(1)).repair(any());
        verify(plans).save(result);
    }

    @Test
    void source_insufficient_and_system_contract_failures_stop_without_model_retry() {
        for (Repairability classification : List.of(Repairability.SOURCE_EVIDENCE_INSUFFICIENT,
                Repairability.SYSTEM_CONTRACT_ERROR)) {
            var session = draftSession();
            var sessions = mock(AdventureSessionRepository.class);
            var plans = mock(AdventureStoryPlanRepository.class);
            var generator = mock(AdventureStoryPlanGenerationPort.class);
            when(sessions.findById(session.id())).thenReturn(Optional.of(session));
            when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
            String rejected = "{\"stages\":[{\"position\":1}]}";
            var violation = new AdventureStoryPlanProjectionViolation("STOP", 1, "stages[0].evidence",
                    "citation-999", "citation-999", classification, "honest stop");
            when(generator.generate(any())).thenThrow(new AdventureStoryPlanCandidateValidationException(List.of(violation), rejected, true));

            var result = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                    .generate(session.id(), session.ownerPlayerId(), new AdventurePlanConfiguration(2, AdventureLength.SHORT));

            assertEquals(AdventureStoryPlanStatus.BLOCKED, result.status());
            verify(generator, never()).repair(any());
            verify(generator, times(1)).generate(any());
        }
    }

    @Test
    void repair_request_carries_computed_dependency_scope_for_a_combat_blocker() {
        var session = draftSession();
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        var generator = mock(AdventureStoryPlanGenerationPort.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());
        String candidate = shortCandidate("bad");
        var blocker = new AdventureStoryPlanProjectionViolation("COMBAT_PARTICIPANT_SOURCE_REQUIRED", 1,
                "stages[0].combatSkeleton.participants[0].name", "", "authoritative field evidence",
                Repairability.REPAIRABLE, "participant evidence is required");
        when(generator.generate(any())).thenThrow(new AdventureStoryPlanCandidateValidationException(
                List.of(blocker), candidate, true));
        when(generator.repair(any())).thenReturn(new AdventureStoryPlanGenerationPort.ProjectionCandidate(
                candidate, shortStages("bad")));

        var result = new AdventureStoryPlanApplicationService(plans, sessions, null, generator)
                .generate(session.id(), session.ownerPlayerId(), new AdventurePlanConfiguration(2, AdventureLength.SHORT));

        assertEquals(AdventureStoryPlanStatus.READY, result.status());
        var request = org.mockito.ArgumentCaptor.forClass(AdventureStoryPlanGenerationPort.RepairRequest.class);
        verify(generator).repair(request.capture());
        assertTrue(request.getValue().repairScope().allows("stages[0].combatSkeleton.objective"));
        assertTrue(request.getValue().repairScope().allows("stages[0].tacticalPreparationRequirement"));
        assertTrue(request.getValue().repairScope().allows("stages[0].sourceFactClaims[*]"));
    }

    @Test
    void collects_map_source_citation_and_graph_violations_without_short_circuiting() throws Exception {
        var citation = new AdventureStoryPlanGenerationPort.SourceCitation(
                "STORYBOOK", UUID.randomUUID(), 1, "page:1", "A rat swarm guards the cellar.", .9);
        var evidence = new AdventurePlanEvidence(citation.documentType(), citation.documentId(), citation.extractionVersion(),
                citation.locator(), citation.quote(), citation.confidence(), citation.provenance());
        var stage = new AdventureStoryPlanStage(1, "Cellar", "Goal", "Conflict", "invented transition",
                List.of(), List.of("ending-1"), List.of(), AdventureStageType.EVENT, "Cellar", null, "", "",
                List.of(), "invented boss", "invented clear", "", List.of("invented reward"), List.of("ending-1"), List.of(evidence),
                AdventureGroundingStatus.GROUNDED, List.of(), "UNAVAILABLE", null);
        var nonMappedStage = new AdventureStoryPlanStage(2, "No Map", "Goal", "Conflict", "Next",
                List.of(), List.of("ending-1"), List.of(), AdventureStageType.EVENT, "No Map", null, "", "",
                List.of(), "", "Next", "", List.of(), List.of("ending-1"), List.of(),
                AdventureGroundingStatus.AI_SUGGESTION, List.of(), "UNAVAILABLE", null);
        var mappedStage = new AdventureStoryPlanStage(3, "Map", "Goal", "Conflict", "Next",
                List.of(), List.of("ending-1"), List.of(), AdventureStageType.EVENT, "Map", UUID.randomUUID(), "", "",
                List.of(), "", "Next", "", List.of(), List.of("ending-1"), List.of(),
                AdventureGroundingStatus.AI_SUGGESTION, List.of(), "UNAVAILABLE", null);
        var request = new AdventureStoryPlanGenerationPort.Request("op", 1, 1,
                new AdventurePlanConfiguration(2, AdventureLength.SHORT), List.of(), List.of(), List.of(),
                List.of(citation, new AdventureStoryPlanGenerationPort.SourceCitation(
                        "RULEBOOK", UUID.randomUUID(), 1, "page:2", "Rat swarm rules.", .9)));
        var service = new AdventureStoryPlanApplicationService(mock(AdventureStoryPlanRepository.class),
                mock(AdventureSessionRepository.class));
        var validate = AdventureStoryPlanApplicationService.class.getDeclaredMethod(
                "validateCandidate", List.class, AdventureStoryPlanGenerationPort.Request.class,
                com.dndmaster.adventure.domain.scenario.ScenarioPackage.class, AdventurePlanConfiguration.class);
        validate.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<AdventureStoryPlanProjectionViolation> violations = (List<AdventureStoryPlanProjectionViolation>) validate.invoke(
                service, List.of(stage, nonMappedStage, mappedStage), request, null, request.configuration());

        assertTrue(violations.stream().anyMatch(item -> item.code().equals("UNKNOWN_MAP_DEFINITION")));
        assertTrue(violations.stream().anyMatch(item -> item.code().equals("SOURCE_CLAIM_UNSUPPORTED")));
        assertTrue(violations.stream().anyMatch(item -> item.code().equals("MISSING_STAGE_EVIDENCE")
                && item.fieldPath().equals("stages[1].evidence")));
        assertTrue(violations.stream().anyMatch(item -> item.code().equals("MISSING_STAGE_EVIDENCE")
                && item.fieldPath().equals("stages[2].evidence")));
        assertTrue(violations.stream().anyMatch(item -> item.code().equals("CITATION_COVERAGE_MISSING")));
        assertTrue(violations.stream().anyMatch(item -> item.code().equals("GRAPH_VALIDATION_FAILED")));
    }

    @Test
    void compatibility_generator_honors_long_configuration() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), java.util.List.of(), "ollama", java.util.List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(plans.findBySessionId(session.id())).thenReturn(Optional.empty());

        var plan = new AdventureStoryPlanApplicationService(plans, sessions)
                .generate(session.id(), session.ownerPlayerId(), new AdventurePlanConfiguration(4, AdventureLength.LONG));

        assertEquals(7, plan.stageCount());
        assertEquals(4, plan.stages().stream().flatMap(stage -> stage.endingIds().stream()).distinct().count());
    }

    private static AdventureSession draftSession() {
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1,
                new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), List.of(), "ollama", List.of("search"), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, false, false, false, false, false, false));
        return session;
    }

    private static List<AdventureStoryPlanStage> shortStages() {
        return shortStages("Next");
    }

    private static List<AdventureStoryPlanStage> shortStages(String firstTransition) {
        return List.of(
                v2Stage(new AdventureStoryPlanStage(1, "One", "Goal", "Conflict", firstTransition, List.of(), List.of("ending-1"))),
                v2Stage(new AdventureStoryPlanStage(2, "Two", "Goal", "Conflict", "Next", List.of(), List.of("ending-2"))),
                v2Stage(new AdventureStoryPlanStage(3, "Three", "Goal", "Conflict", "Next", List.of(), List.of("ending-1"))));
    }

    private static AdventureStoryPlanStage v2Stage(AdventureStoryPlanStage stage) {
        return stage.withCombat(com.dndmaster.adventure.domain.adventure.CombatRequirement.NONE,
                com.dndmaster.adventure.domain.adventure.CombatSkeleton.empty(), List.of(),
                com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement.NOT_REQUIRED);
    }

    private static String shortCandidate(String transition) {
        return "{\"stages\":["
                + stageJson(1, "One", "ending-1", transition) + ","
                + stageJson(2, "Two", "ending-2", "Next") + ","
                + stageJson(3, "Three", "ending-1", "Next") + "]}";
    }

    private static String stageJson(int position, String title, String ending, String transition) {
        return "{\"position\":" + position + ",\"title\":\"" + title
                + "\",\"goal\":\"Goal\",\"conflict\":\"Conflict\",\"transitionCondition\":\""
                + transition + "\",\"npcOrClues\":[],\"endingIds\":[\"" + ending
                + "\"],\"stageType\":\"EVENT\",\"location\":\"" + title
                + "\",\"mapDefinitionId\":\"\",\"mapAssetId\":\"\",\"mapAssetLocator\":\"\",\"enemies\":[],\"boss\":\"\",\"clearCondition\":\""
                + transition + "\",\"failureCondition\":\"\",\"rewards\":[],\"branchIds\":[\"" + ending + "\"],\"branchTargets\":{},\"evidence\":[],"
                + "\"schemaVersion\":2,\"combatRequirement\":\"NONE\",\"combatSkeleton\":{\"objective\":\"\",\"startTrigger\":\"\",\"participants\":[],\"successOutcome\":\"\",\"failureOutcome\":\"\",\"rewards\":[]},\"sourceFactClaims\":[],\"tacticalPreparationRequirement\":\"NOT_REQUIRED\"}";
    }

    private static AdventureStoryPlanProjectionViolation violation(String code, String path) {
        return new AdventureStoryPlanProjectionViolation(code, 1, path, "bad", "", Repairability.REPAIRABLE,
                code + " is not usable");
    }
}
