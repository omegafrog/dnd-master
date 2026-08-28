package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.runtime.TacticalScenePreparationApplicationService;
import com.dndmaster.adventure.application.runtime.TacticalScenePreparationJobRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TacticalScenePreparationApplicationServiceTest {
    @Test
    void prepares_only_the_current_stage_and_reuses_duplicate_request() {
        SessionId sessionId = new SessionId(UUID.randomUUID());
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = mock(AdventureSession.class);
        AdventureStoryPlanRepository plans = mock(AdventureStoryPlanRepository.class);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureStoryPlanGenerationPort generator = mock(AdventureStoryPlanGenerationPort.class);
        TacticalScenePlanValidator validator = mock(TacticalScenePlanValidator.class);
        AdventureStoryPlanStage current = stage(1);
        AdventureStoryPlanStage future = stage(2);
        AdventureStoryPlan plan = AdventureStoryPlan.ready(UUID.randomUUID(), sessionId, 1, 1, 1, List.of(current, future));
        TacticalScenePlan ready = mock(TacticalScenePlan.class);
        when(session.ownerPlayerId()).thenReturn(owner);
        when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        when(session.party()).thenReturn(List.of());
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(plans.findBySessionId(sessionId)).thenReturn(Optional.of(plan));
        when(generator.generateTacticalScene(any())).thenReturn(new TacticalScenePlanCandidate(1, ready, List.of()));
        when(validator.validate(any(), any())).thenReturn(List.of());
        when(ready.readyForActivation()).thenReturn(true);

        var service = new TacticalScenePreparationApplicationService(plans, sessions, generator, validator);
        var first = service.prepare(sessionId, owner);
        var duplicate = service.prepare(sessionId, owner);

        assertEquals(TacticalScenePreparationApplicationService.Status.COMPLETE, first.status());
        assertEquals(first, duplicate);
        verify(generator).generateTacticalScene(any());
        verify(plans).save(any());
    }

    @Test
    void stops_after_three_invalid_candidates_without_touching_future_stage() {
        SessionId sessionId = new SessionId(UUID.randomUUID());
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = mock(AdventureSession.class);
        AdventureStoryPlanRepository plans = mock(AdventureStoryPlanRepository.class);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureStoryPlanGenerationPort generator = mock(AdventureStoryPlanGenerationPort.class);
        TacticalScenePlanValidator validator = mock(TacticalScenePlanValidator.class);
        AdventureStoryPlan plan = AdventureStoryPlan.ready(UUID.randomUUID(), sessionId, 1, 1, 1, List.of(stage(1), stage(2)));
        when(session.ownerPlayerId()).thenReturn(owner);
        when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        when(session.party()).thenReturn(List.of());
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(plans.findBySessionId(sessionId)).thenReturn(Optional.of(plan));
        when(generator.generateTacticalScene(any())).thenReturn(TacticalScenePlanCandidate.absent(1));
        when(validator.validate(any(), any())).thenReturn(List.of("tactical scene is absent"));

        var result = new TacticalScenePreparationApplicationService(plans, sessions, generator, validator).prepare(sessionId, owner);

        assertEquals(TacticalScenePreparationApplicationService.Status.FAILED_RETRYABLE, result.status());
        assertEquals(3, result.attempts());
        assertEquals(1, result.stagePosition());
        verify(generator, org.mockito.Mockito.times(3)).generateTacticalScene(any());
        verify(plans, never()).save(any());
    }

    @Test
    void falls_back_to_minimal_ready_scene_after_generator_failure() {
        SessionId sessionId = new SessionId(UUID.randomUUID());
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = mock(AdventureSession.class);
        AdventureStoryPlanRepository plans = mock(AdventureStoryPlanRepository.class);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureStoryPlanGenerationPort generator = mock(AdventureStoryPlanGenerationPort.class);
        TacticalScenePlanValidator validator = mock(TacticalScenePlanValidator.class);
        var sheet = new CharacterSheetId(UUID.randomUUID());
        AdventureStoryPlan plan = AdventureStoryPlan.ready(UUID.randomUUID(), sessionId, 1, 1, 1, List.of(stage(1)));
        when(session.ownerPlayerId()).thenReturn(owner);
        when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        when(session.party()).thenReturn(List.of(new AdventurePartyMember(sheet, ControlMode.DIRECT,
                false, false, false, false, false, false)));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(plans.findBySessionId(sessionId)).thenReturn(Optional.of(plan));
        when(generator.generateTacticalScene(any())).thenThrow(new IllegalStateException("generator unavailable"));

        var result = new TacticalScenePreparationApplicationService(plans, sessions, generator, validator)
                .prepare(sessionId, owner);

        assertEquals(TacticalScenePreparationApplicationService.Status.COMPLETE, result.status());
        assertEquals("generator unavailable", result.failureReason());
        verify(plans).save(org.mockito.ArgumentMatchers.argThat(saved -> {
            var prepared = saved.stages().get(0).tacticalScenePlan();
            return prepared.readyForActivation() && prepared.players().size() == 1
                    && prepared.players().get(0).id().equals("player-" + sheet.value())
                    && prepared.enemies().isEmpty() && prepared.npcs().isEmpty()
                    && prepared.bosses().isEmpty() && prepared.interactiveObjects().isEmpty()
                    && prepared.triggers().size() == 1 && prepared.outcomes().size() == 1;
        }));
    }

    @Test
    void requeues_stale_failed_job_on_prepare_when_safe_fallback_is_available() {
        SessionId sessionId = new SessionId(UUID.randomUUID());
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = mock(AdventureSession.class);
        AdventureStoryPlanRepository plans = mock(AdventureStoryPlanRepository.class);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureStoryPlanGenerationPort generator = mock(AdventureStoryPlanGenerationPort.class);
        TacticalScenePlanValidator validator = mock(TacticalScenePlanValidator.class);
        TacticalScenePreparationJobRepository jobs = mock(TacticalScenePreparationJobRepository.class);
        var sheet = new CharacterSheetId(UUID.randomUUID());
        var failed = new TacticalScenePreparationJobRepository.Job(UUID.randomUUID(), sessionId.value(), owner.value(), 1,
                "Stage 1", TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE, 100, 3, true, "failed", "offline", Instant.now());
        var current = new TacticalScenePreparationJobRepository.Job(failed.jobId(), failed.sessionId(), failed.ownerId(), 1,
                failed.stageName(), TacticalScenePreparationJobRepository.Status.QUEUED, 0, 0, true, "queued", null, Instant.now());
        var state = new TacticalScenePreparationJobRepository.Job[] { failed };
        when(jobs.createOrGet(any(), any(), org.mockito.ArgumentMatchers.eq(1), any(), org.mockito.ArgumentMatchers.eq(true))).thenReturn(failed);
        when(jobs.find(any(), org.mockito.ArgumentMatchers.eq(1))).thenAnswer(invocation -> Optional.of(state[0]));
        org.mockito.Mockito.doAnswer(invocation -> { state[0] = current; return null; }).when(jobs).resetForRetry(failed.jobId());
        when(jobs.claim(failed.jobId())).thenAnswer(invocation -> { state[0] = new TacticalScenePreparationJobRepository.Job(current.jobId(), current.sessionId(), current.ownerId(), 1, current.stageName(), TacticalScenePreparationJobRepository.Status.RUNNING, 0, 0, true, "running", null, Instant.now()); return true; });
        org.mockito.Mockito.doAnswer(invocation -> { state[0] = new TacticalScenePreparationJobRepository.Job(state[0].jobId(), state[0].sessionId(), state[0].ownerId(), 1, state[0].stageName(), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), true, invocation.getArgument(4), invocation.getArgument(5), Instant.now()); return null; }).when(jobs).update(any(), any(), any(Integer.class), any(Integer.class), any(), any());
        when(session.ownerPlayerId()).thenReturn(owner);
        when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        when(session.party()).thenReturn(List.of(new AdventurePartyMember(sheet, ControlMode.DIRECT, false, false, false, false, false, false)));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(plans.findBySessionId(sessionId)).thenReturn(Optional.of(AdventureStoryPlan.ready(UUID.randomUUID(), sessionId, 1, 1, 1, List.of(stage(1)))));
        when(generator.generateTacticalScene(any())).thenThrow(new IllegalStateException("still unavailable"));

        var result = new TacticalScenePreparationApplicationService(plans, sessions, generator, validator, jobs).prepare(sessionId, owner);

        assertEquals(TacticalScenePreparationApplicationService.Status.COMPLETE, result.status());
        verify(jobs).resetForRetry(failed.jobId());
    }

    private static AdventureStoryPlanStage stage(int position) {
        return new AdventureStoryPlanStage(position, "Stage " + position, "goal", "conflict", "exit", List.of(), List.of(),
                List.of(), com.dndmaster.adventure.domain.adventure.AdventureStageType.DUNGEON, "location", UUID.randomUUID(),
                "asset", "locator", List.of(), "", "clear", "fail", List.of(), List.of(), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED, List.of(), "SAFE", 1.0);
    }
}
