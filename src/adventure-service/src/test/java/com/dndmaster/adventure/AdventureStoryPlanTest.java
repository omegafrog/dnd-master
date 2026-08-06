package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanTest {
    @Test
    void ready_plan_freezes_snapshot_and_exposes_only_safe_metadata() {
        var sessionId = SessionId.generate();
        var plan = AdventureStoryPlan.ready(sessionId, 4, 2, List.of(
                new AdventureStoryPlanStage(1, "The hidden title", "goal", "conflict", "transition", List.of("NPC"), List.of("ending"))));

        assertEquals(AdventureStoryPlanStatus.READY, plan.status());
        assertEquals(4, plan.partyRevision());
        assertEquals(2, plan.version());
        assertEquals(1, plan.stageCount());
        assertThrows(UnsupportedOperationException.class, () -> plan.stages().add(null));
    }

    @Test
    void plan_rejects_empty_stages() {
        assertThrows(IllegalArgumentException.class, () -> AdventureStoryPlan.ready(
                SessionId.generate(), 1, 1, List.of()));
    }

    @Test
    void preserves_generating_status_when_rehydrated() {
        var plan = AdventureStoryPlan.rehydrate(UUID.randomUUID(), SessionId.generate(), 2, 3, 1,
                AdventureStoryPlanStatus.GENERATING, List.of(), 0, null, Instant.now());
        assertEquals(AdventureStoryPlanStatus.GENERATING, plan.status());
        assertEquals(0, plan.stageCount());
    }
}
