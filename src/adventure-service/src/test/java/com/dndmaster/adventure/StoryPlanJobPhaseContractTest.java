package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationJobService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.time.Instant;
import java.util.UUID;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StoryPlanJobPhaseContractTest {
    @Test
    void exposesAdditivePhaseWithoutChangingLegacyJobFields() {
        var job = new AdventureStoryPlanGenerationJobService.JobView(
                UUID.randomUUID(), UUID.randomUUID(), AdventureStoryPlanGenerationJobService.Status.RUNNING,
                25, "모험 개요 생성 중", null, Instant.now());

        assertEquals(25, job.progress());
        assertEquals("모험 개요 생성 중", job.stage());
        assertEquals(AdventureStoryPlanGenerationJobService.Phase.GENERATING_STORY_PLAN, job.phase());
    }

    @Test
    void mapsTerminalStagesToTerminalPhases() {
        assertEquals(AdventureStoryPlanGenerationJobService.Phase.COMPLETE,
                AdventureStoryPlanGenerationJobService.Phase.from("플레이 준비 완료"));
        assertEquals(AdventureStoryPlanGenerationJobService.Phase.FAILED,
                AdventureStoryPlanGenerationJobService.Phase.from("계획 생성 실패"));
        assertEquals(AdventureStoryPlanGenerationJobService.Phase.REPAIRING_STORY_PLAN,
                AdventureStoryPlanGenerationJobService.Phase.from("계획 검증 실패, 재시도 준비 중 (1/5)"));
    }

    @Test
    void exposes_blocked_plan_as_blocked_job_result() throws Exception {
        var sessionId = SessionId.generate();
        var owner = new OwnerPlayerId(UUID.randomUUID());
        var sessions = mock(AdventureSessionRepository.class);
        var plans = mock(AdventureStoryPlanApplicationService.class);
        var blocked = mock(AdventureStoryPlan.class);
        var session = mock(com.dndmaster.adventure.domain.adventure.AdventureSession.class);
        when(session.ownerPlayerId()).thenReturn(owner);
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        when(plans.generate(eq(sessionId), eq(owner), any(), any())).thenReturn(blocked);
        when(blocked.status()).thenReturn(AdventureStoryPlanStatus.BLOCKED);
        when(blocked.failureReason()).thenReturn("source contradiction");

        try (var jobs = new AdventureStoryPlanGenerationJobService(plans, sessions, Duration.ofSeconds(1))) {
            var job = jobs.start(sessionId, owner, com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration.defaults());
            AdventureStoryPlanGenerationJobService.JobView completed;
            do {
                Thread.sleep(10);
                completed = jobs.read(job.jobId(), sessionId, owner);
            } while (completed.status() == AdventureStoryPlanGenerationJobService.Status.QUEUED
                    || completed.status() == AdventureStoryPlanGenerationJobService.Status.RUNNING);
            assertEquals(AdventureStoryPlanGenerationJobService.Status.BLOCKED, completed.status());
            assertEquals(AdventureStoryPlanGenerationJobService.Phase.BLOCKED, completed.phase());
        }
    }
}
