package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.TacticalScenePreparationApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanValidator;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.CombatParticipant;
import com.dndmaster.adventure.domain.adventure.CombatRequirement;
import com.dndmaster.adventure.domain.adventure.CombatSkeleton;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalPreparationCompositionTest {
    @Test
    void future_required_stage_is_pending_without_creating_a_job_or_scene() {
        SessionId sessionId = new SessionId(UUID.randomUUID());
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSession session = mock(AdventureSession.class);
        AdventureStoryPlanRepository plans = mock(AdventureStoryPlanRepository.class);
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        AdventureStoryPlanGenerationPort generator = mock(AdventureStoryPlanGenerationPort.class);
        when(session.ownerPlayerId()).thenReturn(owner);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(plans.findBySessionId(sessionId)).thenReturn(Optional.of(AdventureStoryPlan.ready(sessionId, 1, 1,
                List.of(stage(1, false), stage(2, true)))));

        var service = new TacticalScenePreparationApplicationService(plans, sessions, generator,
                new TacticalScenePlanValidator());

        var view = service.readComposed(sessionId, owner, 2);

        assertEquals("REQUIRED_PENDING", view.state().name());
        assertEquals(Optional.empty(), view.job());
        verify(generator, never()).generateTacticalScene(any());
    }

    private static AdventureStoryPlanStage stage(int position, boolean required) {
        AdventureStoryPlanStage stage = new AdventureStoryPlanStage(position, "Stage " + position, "goal", "conflict", "exit",
                List.of(), List.of(), List.of(), AdventureStageType.DUNGEON, "location", UUID.randomUUID(), "asset", "locator",
                List.of(), "", "clear", "fail", List.of(), List.of(), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.GROUNDED, List.of(), "SAFE", 1.0,
                Map.of(), 0, 0, "UNAVAILABLE", "");
        if (!required) return stage;
        return stage.withCombat(CombatRequirement.REQUIRED,
                new CombatSkeleton("Defeat rats", "Enter", List.of(CombatParticipant.enemy("rat", "rat", 1, 2, List.of())),
                        "Rats defeated", "Retreat", List.of()), List.of(), TacticalPreparationRequirement.REQUIRED);
    }
}
