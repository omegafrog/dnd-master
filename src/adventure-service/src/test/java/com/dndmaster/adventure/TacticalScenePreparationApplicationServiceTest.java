package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.runtime.TacticalScenePreparationApplicationService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
        AdventureStoryPlan preparedPlan = plan.prepareCurrentStage(current.withTacticalScenePlan(ready));
        when(session.ownerPlayerId()).thenReturn(owner);
        when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        when(session.party()).thenReturn(List.of());
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(plans.findBySessionId(sessionId)).thenReturn(Optional.of(plan), Optional.of(preparedPlan));
        when(generator.generateTacticalScene(any())).thenReturn(new TacticalScenePlanCandidate(1, ready, List.of()));
        when(validator.validate(any(), any())).thenReturn(List.of());
        when(ready.readyForActivation()).thenReturn(true);
        when(ready.status()).thenReturn(com.dndmaster.adventure.domain.adventure.TacticalScenePlanStatus.READY);

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
    void retries_only_after_an_explicit_retry_and_keeps_the_same_job() {
        SessionId sessionId = new SessionId(UUID.randomUUID());
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = mock(AdventureSession.class);
        AdventureStoryPlanRepository plans = mock(AdventureStoryPlanRepository.class);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureStoryPlanGenerationPort generator = mock(AdventureStoryPlanGenerationPort.class);
        TacticalScenePlanValidator validator = mock(TacticalScenePlanValidator.class);
        AdventureStoryPlanStage current = stage(1);
        AdventureStoryPlan plan = AdventureStoryPlan.ready(UUID.randomUUID(), sessionId, 1, 1, 1, List.of(current, stage(2)));
        TacticalScenePlan ready = mock(TacticalScenePlan.class);
        AdventureStoryPlan preparedPlan = plan.prepareCurrentStage(current.withTacticalScenePlan(ready));
        when(session.ownerPlayerId()).thenReturn(owner);
        when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        when(session.party()).thenReturn(List.of());
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(plans.findBySessionId(sessionId)).thenReturn(Optional.of(plan), Optional.of(plan), Optional.of(plan),
                Optional.of(plan), Optional.of(plan), Optional.of(preparedPlan));
        when(generator.generateTacticalScene(any())).thenReturn(TacticalScenePlanCandidate.absent(1),
                TacticalScenePlanCandidate.absent(1), TacticalScenePlanCandidate.absent(1),
                new TacticalScenePlanCandidate(1, ready, List.of()));
        when(validator.validate(any(), any())).thenReturn(List.of("temporary failure"), List.of("temporary failure"),
                List.of("temporary failure"), List.of());
        when(ready.readyForActivation()).thenReturn(true);
        when(ready.status()).thenReturn(com.dndmaster.adventure.domain.adventure.TacticalScenePlanStatus.READY);

        var service = new TacticalScenePreparationApplicationService(plans, sessions, generator, validator);
        var failed = service.prepare(sessionId, owner);
        var retried = service.retry(sessionId, owner);

        assertEquals(com.dndmaster.adventure.application.runtime.TacticalPreparationState.FAILED_RETRYABLE, failed.state());
        assertEquals(com.dndmaster.adventure.application.runtime.TacticalPreparationState.READY, retried.state());
        assertEquals(failed.jobId(), retried.jobId());
        verify(generator, org.mockito.Mockito.times(4)).generateTacticalScene(any());
        verify(plans).save(any());
    }

    private static AdventureStoryPlanStage stage(int position) {
        var stage = new AdventureStoryPlanStage(position, "Stage " + position, "goal", "conflict", "exit", List.of(), List.of(),
                List.of(), com.dndmaster.adventure.domain.adventure.AdventureStageType.DUNGEON, "location", UUID.randomUUID(),
                "asset", "locator", List.of(), "", "clear", "fail", List.of(), List.of(), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED, List.of(), "SAFE", 1.0);
        return stage.withCombat(com.dndmaster.adventure.domain.adventure.CombatRequirement.REQUIRED,
                new com.dndmaster.adventure.domain.adventure.CombatSkeleton("defeat the enemy", "enter", List.of(
                        com.dndmaster.adventure.domain.adventure.CombatParticipant.enemy("enemy", "enemy", 1, 1, List.of())),
                        "victory", "retreat", List.of()), List.of(),
                com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement.REQUIRED);
    }
}
