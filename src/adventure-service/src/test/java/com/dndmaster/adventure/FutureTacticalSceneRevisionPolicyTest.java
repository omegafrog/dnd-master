package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.application.runtime.TacticalTriggerEvaluator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.dndmaster.adventure.application.storyplan.FutureTacticalSceneRevisionService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanRepository;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;

class FutureTacticalSceneRevisionPolicyTest {
    @Test
    void preservesRevealedStagesAndCreatesSuccessorRevisionForFutureStages() {
        var plan = AdventureStoryPlan.ready(new SessionId(java.util.UUID.randomUUID()), 0, 1, List.of(stage(1, "published"), stage(2, "future"))).advanceTo(0);

        var next = plan.reviseFutureStages(List.of(stage(1, "published"), stage(2, "revised future")));

        assertEquals(3, next.version());
        assertEquals("published", next.stages().getFirst().title());
        assertEquals("revised future", next.stages().get(1).title());
    }

    @Test
    void rejectsRevisionOfPublishedStage() {
        var plan = AdventureStoryPlan.ready(new SessionId(java.util.UUID.randomUUID()), 0, 1, List.of(stage(1, "published"), stage(2, "future"))).advanceTo(0);

        assertThrows(IllegalStateException.class, () -> plan.reviseFutureStages(List.of(stage(1, "changed"), stage(2, "future"))));
    }

    @Test
    void postStartCommandRevisesOnlyAnUnrevealedFutureStage() {
        var sessionId = new SessionId(java.util.UUID.randomUUID());
        var owner = new OwnerPlayerId(java.util.UUID.randomUUID());
        var session = mock(AdventureSession.class);
        when(session.ownerPlayerId()).thenReturn(owner);
        when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        var plan = AdventureStoryPlan.ready(sessionId, 0, 1, List.of(stage(1, "current"), stage(2, "future"))).advanceTo(0);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(plans.findBySessionId(sessionId)).thenReturn(java.util.Optional.of(plan));
        var sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        var service = new FutureTacticalSceneRevisionService(plans, sessions);

        service.revise(sessionId, owner, 2, stage(2, "future revised").tacticalScenePlan());

        verify(plans).save(argThat(value -> value.version() == 3
                && value.stages().get(0).title().equals("current")
                && value.stages().get(1).title().equals("future")));
        assertThrows(IllegalStateException.class, () -> service.revise(sessionId, owner, 1, stage(1, "changed").tacticalScenePlan()));
    }

    @Test
    void evaluatesOnlyAnAuthoredTrigger() {
        var grounding = PlacementGrounding.aiInference("bounded");
        var scene = new TacticalScenePlan(1, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(new TacticalPlacement("hero", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding),
                List.of(new TacticalTrigger("alarm", TacticalTriggerType.ALARM, List.of("hero"), "", grounding)), List.of(), List.of());
        var evaluator = new TacticalTriggerEvaluator();

        assertEquals("ALARM", evaluator.evaluate(scene, "alarm").type());
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(scene, "invented"));
    }

    private static AdventureStoryPlanStage stage(int position, String title) {
        return new AdventureStoryPlanStage(position, title, "goal", "conflict", "transition", List.of(), List.of("end"));
    }
}
