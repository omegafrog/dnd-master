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
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.scenario.ResolutionStatus;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilationReport;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
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

        var service = new AdventureStoryPlanApplicationService(plans, sessions, packages, request -> java.util.List.of());

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
        when(generator.generate(any())).thenReturn(java.util.List.of(
                new AdventureStoryPlanStage(1, "One", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(2, "Two", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(3, "Three", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(4, "Four", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(5, "Five", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a")),
                new AdventureStoryPlanStage(6, "Six", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-b")),
                new AdventureStoryPlanStage(7, "Seven", "Goal", "Conflict", "Next", java.util.List.of(), java.util.List.of("ending-a"))));

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
                .thenReturn(readyStages);

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
    void retries_candidate_validation_up_to_five_attempts_before_blocking() {
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
        verify(generator, times(5)).generate(any());
        assertTrue(result.failureReason().contains("hidden trigger outcomes are incomplete"));
        verify(plans).save(result);
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
}
