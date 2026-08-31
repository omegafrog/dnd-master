package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationJobService;
import java.time.Instant;
import java.util.UUID;
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
}
