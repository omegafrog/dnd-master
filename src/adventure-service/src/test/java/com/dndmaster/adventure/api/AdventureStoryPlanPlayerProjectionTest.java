package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import com.dndmaster.adventure.application.runtime.TacticalMapActivationApplicationService;
import com.dndmaster.adventure.application.runtime.TacticalTriggerRuntimeApplicationService;
import com.dndmaster.adventure.application.storyplan.FutureTacticalSceneRevisionService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;

class AdventureStoryPlanPlayerProjectionTest {
    @Test
    void playerProjectionContainsOnlyTheCurrentStageAndNoPlanRevisionMetadata() {
        AdventureStoryPlan plan = AdventureStoryPlan.ready(SessionId.generate(), 4, 3, List.of(
                new AdventureStoryPlanStage(1, "Revealed", "Goal", "Conflict", "Next", List.of(), List.of("ending-a")),
                new AdventureStoryPlanStage(2, "Hidden", "Secret goal", "Secret conflict", "Secret next", List.of(), List.of("ending-b"))));
        plan = plan.advanceTo(1);

        AdventureStoryPlanController.PlayerPlanView view = AdventureStoryPlanController.PlayerPlanView.from(plan);

        assertEquals("READY", view.status());
        assertEquals(1, view.currentStage());
        assertEquals(1, view.stages().size());
        assertEquals("Hidden", view.stages().getFirst().title());
        assertEquals(0, view.planRevision());
        String json;
        try { json = new ObjectMapper().writeValueAsString(view); }
        catch (Exception exception) { throw new AssertionError(exception); }
        assertFalse(json.contains("rewards"));
        assertFalse(json.contains("mapDefinitionId"));
        assertFalse(json.contains("groundingStatus"));
        assertFalse(json.contains("playerSpawnX"));
        assertFalse(json.contains("failureReason"));
    }

    @Test
    void playerCannotReadStoryPlanHistory() {
        var controller = new AdventureStoryPlanController(null, null, null, null, null, null, new ApiRequestGuard("test-internal-token"));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> controller.history(UUID.randomUUID()));

        assertEquals(403, failure.getStatusCode().value());
    }

    @Test
    void internalHistoryReturnsOwnerScopedGmRevisionProjection() {
        var sessionId = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var resolver = mock(AuthenticatedPlayerResolver.class);
        var stories = mock(AdventureStoryPlanApplicationService.class);
        var plan = AdventureStoryPlan.ready(new SessionId(sessionId), 4, 3, List.of(
                new AdventureStoryPlanStage(1, "Revealed", "Goal", "Conflict", "Next", List.of(), List.of("ending-a"))));
        when(resolver.playerId()).thenReturn(owner);
        var historyId = UUID.randomUUID();
        var predecessorId = UUID.randomUUID();
        when(stories.readHistoryEntries(new SessionId(sessionId), new OwnerPlayerId(owner))).thenReturn(List.of(
                new com.dndmaster.adventure.application.storyplan.AdventureStoryPlanHistoryEntry(plan, historyId, plan.updatedAt(), "GM_TURN:" + UUID.randomUUID(), predecessorId)));
        var controller = new AdventureStoryPlanController(stories, null, null, resolver, null, null, new ApiRequestGuard("test-internal-token"));

        var history = controller.history(sessionId, "test-internal-token");

        assertEquals(1, history.size());
        assertEquals(plan.planId(), history.getFirst().planId());
        assertEquals(historyId.toString(), history.getFirst().auditId());
        assertEquals(plan.updatedAt(), history.getFirst().recordedAt());
        assertTrue(history.getFirst().cause().startsWith("GM_TURN:"));
        assertEquals(predecessorId.toString(), history.getFirst().predecessorHistoryId());
        verify(stories).readHistoryEntries(new SessionId(sessionId), new OwnerPlayerId(owner));
    }

    @Test
    void internalHistoryRejectsMissingTokenWith401() {
        var controller = new AdventureStoryPlanController(null, null, null, null, null, null, new ApiRequestGuard("test-internal-token"));

        var failure = assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.history(UUID.randomUUID(), null));

        assertEquals(401, failure.status());
    }

    @Test
    void internalHistoryMapsForeignSessionTo403() {
        var sessionId = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var resolver = mock(AuthenticatedPlayerResolver.class);
        var stories = mock(AdventureStoryPlanApplicationService.class);
        when(resolver.playerId()).thenReturn(owner);
        when(stories.readHistoryEntries(new SessionId(sessionId), new OwnerPlayerId(owner)))
                .thenThrow(new SecurityException("adventure session access denied"));
        var controller = new AdventureStoryPlanController(stories, null, null, resolver, null, null, new ApiRequestGuard("test-internal-token"));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> controller.history(sessionId, "test-internal-token"));

        assertEquals(403, failure.getStatusCode().value());
    }

    @Test
    void blockedPlayerProjectionIncludesOnlyUsableRetryDiagnostics() {
        var sessionId = SessionId.generate();
        AdventureStoryPlan plan = AdventureStoryPlan.blocked(
                UUID.randomUUID(), sessionId, 1, 0, 1,
                com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration.defaults(),
                List.of(), "근거 없는 적 배치");

        AdventureStoryPlanController.PlayerPlanView view = AdventureStoryPlanController.PlayerPlanView.from(plan);

        assertEquals("BLOCKED", view.status());
        assertEquals("근거 없는 적 배치", view.failureReason());
        assertEquals(2, view.endingCount());
        assertEquals("STANDARD", view.adventureLength());
    }

    @Test
    void fullGmProjectionRequiresInternalAuthorization() {
        var controller = new AdventureStoryPlanController(null, null, null, null, null, null, new ApiRequestGuard("production-secret"));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.gm(UUID.randomUUID(), "wrong-token"));
    }

    @Test
    void publicActivationRejectsAnArbitraryFutureStage() {
        var sessionId = UUID.randomUUID();
        var owner = UUID.randomUUID();
        var adventure = UUID.randomUUID();
        var sessions = mock(AdventureSessionApplicationService.class);
        var resolver = mock(AuthenticatedPlayerResolver.class);
        var session = mock(com.dndmaster.adventure.domain.adventure.AdventureSession.class);
        var plan = mock(AdventureStoryPlan.class);
        when(resolver.playerId()).thenReturn(owner);
        when(session.startedAdventureId()).thenReturn(new com.dndmaster.adventure.domain.adventure.AdventureId(adventure));
        when(sessions.read(new SessionId(sessionId), new OwnerPlayerId(owner))).thenReturn(session);
        var stories = mock(AdventureStoryPlanApplicationService.class);
        when(stories.read(new SessionId(sessionId), new OwnerPlayerId(owner))).thenReturn(plan);
        when(plan.currentStage()).thenReturn(0);
        var controller = new AdventureStoryPlanController(stories, sessions, mock(TacticalMapActivationApplicationService.class), resolver,
                mock(TacticalTriggerRuntimeApplicationService.class), mock(FutureTacticalSceneRevisionService.class), new ApiRequestGuard("production-secret"));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> controller.activateMap(sessionId, 2));
        assertEquals(409, failure.getStatusCode().value());
    }

    @Test
    void playerCannotReviseFutureTacticalSceneWithoutInternalAuthorization() {
        var controller = new AdventureStoryPlanController(null, null, null, null, null, null, new ApiRequestGuard("test-internal-token"));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.reviseFutureTacticalScene(
                UUID.randomUUID(), 2, null));
    }
}
