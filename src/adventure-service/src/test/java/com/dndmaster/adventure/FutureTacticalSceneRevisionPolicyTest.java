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
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanGenerationPort;
import com.dndmaster.adventure.application.storyplan.TacticalScenePlanCandidate;
import com.dndmaster.adventure.application.storyplan.TacticalSceneRequest;

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
        var tactical = validScene();
        var plan = AdventureStoryPlan.ready(sessionId, 0, 1, List.of(stage(1, "current"), tacticalStage(2, "future", tactical))).advanceTo(0);
        var plans = mock(AdventureStoryPlanRepository.class);
        when(plans.findBySessionId(sessionId)).thenReturn(java.util.Optional.of(plan));
        var sessions = mock(AdventureSessionRepository.class);
        when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        var service = new FutureTacticalSceneRevisionService(plans, sessions);

        service.revise(sessionId, owner, 2, tactical);

        verify(plans).save(argThat(value -> value.version() == 3
                && value.stages().get(0).title().equals("current")
                && value.stages().get(1).title().equals("future")
                && value.stages().get(1).tacticalScenePlan().status() == TacticalScenePlanStatus.READY));
        assertThrows(IllegalStateException.class, () -> service.revise(sessionId, owner, 1, stage(1, "changed").tacticalScenePlan()));
    }

    @Test
    void rejectsUntrustedInvalidFutureSceneBeforePersistence() {
        var sessionId = new SessionId(java.util.UUID.randomUUID());
        var owner = new OwnerPlayerId(java.util.UUID.randomUUID());
        var session = mock(AdventureSession.class); when(session.ownerPlayerId()).thenReturn(owner); when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        var plan = AdventureStoryPlan.ready(sessionId, 0, 1, List.of(stage(1, "current"), tacticalStage(2, "future", validScene()))).advanceTo(0);
        var plans = mock(AdventureStoryPlanRepository.class); when(plans.findBySessionId(sessionId)).thenReturn(java.util.Optional.of(plan));
        var sessions = mock(AdventureSessionRepository.class); when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        var service = new FutureTacticalSceneRevisionService(plans, sessions);

        assertThrows(IllegalArgumentException.class, () -> service.revise(sessionId, owner, 2, TacticalScenePlan.absent()));
        verify(plans, never()).save(any());
    }

    @Test
    void usesGroundedGeneratorRetriesAndBlocksAfterThreeInvalidCandidates() {
        var sessionId = new SessionId(java.util.UUID.randomUUID());
        var owner = new OwnerPlayerId(java.util.UUID.randomUUID());
        var session = mock(AdventureSession.class); when(session.ownerPlayerId()).thenReturn(owner); when(session.status()).thenReturn(AdventureSession.Status.STARTED);
        var plan = AdventureStoryPlan.ready(sessionId, 0, 1, List.of(stage(1, "current"), tacticalStage(2, "future", validScene()))).advanceTo(0);
        var plans = mock(AdventureStoryPlanRepository.class); when(plans.findBySessionId(sessionId)).thenReturn(java.util.Optional.of(plan));
        var sessions = mock(AdventureSessionRepository.class); when(sessions.findById(sessionId)).thenReturn(java.util.Optional.of(session));
        int[] calls = {0};
        AdventureStoryPlanGenerationPort generator = new AdventureStoryPlanGenerationPort() {
            public List<AdventureStoryPlanStage> generate(Request request) { return List.of(); }
            public TacticalScenePlanCandidate generateTacticalScene(TacticalSceneRequest request) {
                calls[0]++;
                return TacticalScenePlanCandidate.absent(2);
            }
        };
        var service = new FutureTacticalSceneRevisionService(plans, sessions, generator);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.revise(sessionId, owner, 2, validScene()));

        assertEquals(3, calls[0]);
        assertEquals(true, failure.getMessage().contains("blocked after 3 attempts"));
        verify(plans, never()).save(any());
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

    private static AdventureStoryPlanStage tacticalStage(int position, String title, TacticalScenePlan scene) {
        return new AdventureStoryPlanStage(position, title, "goal", "conflict", "transition", List.of(), List.of("end"), List.of(),
                AdventureStageType.ENCOUNTER, "cellar", java.util.UUID.randomUUID(), "map", "map.png", List.of("rat"), "", "clear", "fail",
                List.of("reward"), List.of("end"), List.of(), AdventureGroundingStatus.GROUNDED, List.of(), "SAFE", .9).withTacticalScenePlan(scene);
    }

    private static TacticalScenePlan validScene() {
        var grounding = PlacementGrounding.aiInference("bounded");
        return new TacticalScenePlan(1, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(new TacticalPlacement("hero", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding),
                List.of(new TacticalTrigger("entry", TacticalTriggerType.COMBAT_ENTRY, List.of("hero"), "", grounding)),
                List.of(new TacticalOutcome("win", "clear", grounding)), List.of());
    }
}
