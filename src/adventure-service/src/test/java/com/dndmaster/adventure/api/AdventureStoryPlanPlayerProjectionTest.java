package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        var controller = new AdventureStoryPlanController(null, null, null, null, null, null);

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> controller.history(UUID.randomUUID()));

        assertEquals(403, failure.getStatusCode().value());
    }

    @Test
    void playerCannotReviseFutureTacticalSceneWithoutInternalAuthorization() {
        var controller = new AdventureStoryPlanController(null, null, null, null, null, null);

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.reviseFutureTacticalScene(
                UUID.randomUUID(), 2, null, null));
    }
}
