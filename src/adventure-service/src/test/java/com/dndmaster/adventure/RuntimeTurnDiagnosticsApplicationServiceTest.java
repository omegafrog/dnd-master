package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.ResolvedTurnPlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnDiagnosticsApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnDiagnosticsApplicationService.RuntimeTurnDiagnosticsView;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import com.dndmaster.adventure.api.ApiRequestGuard;
import com.dndmaster.adventure.api.RuntimeTurnDiagnosticsController;
import com.dndmaster.adventure.api.ScenarioExceptionHandler;
import com.dndmaster.adventure.infrastructure.persistence.RuntimeTurnCompatibilityException;
import com.dndmaster.adventure.infrastructure.persistence.RuntimeTurnPersistenceException;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class RuntimeTurnDiagnosticsApplicationServiceTest {
    @Test
    void projects_legacy_presented_turn_without_hidden_or_mutable_context() {
        RuntimeTurnRepository repository = mock(RuntimeTurnRepository.class);
        RuntimeTurn turn = mock(RuntimeTurn.class);
        UUID turnId = UUID.randomUUID();
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "visible prose",
                mock(ActiveSourceContext.class), List.of(), List.of(), "provider", "model", "private reasoning");
        when(repository.findByTurnId(turnId)).thenReturn(java.util.Optional.of(turn));
        when(turn.turnId()).thenReturn(turnId);
        when(turn.commandId()).thenReturn(UUID.randomUUID());
        when(turn.adventureId()).thenReturn(new AdventureId(UUID.randomUUID()));
        when(turn.sessionId()).thenReturn(UUID.randomUUID());
        when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.PRESENTED);
        when(turn.committed()).thenReturn(true);
        when(turn.origin()).thenReturn(RuntimeTurnOrigin.PLAYER);
        when(turn.resolvedArtifact()).thenReturn(null);
        when(turn.plan()).thenReturn(plan);

        RuntimeTurnDiagnosticsView view = new RuntimeTurnDiagnosticsApplicationService(repository).readByTurnId(turnId).orElseThrow();

        assertEquals("visible prose", view.writer().prose());
        assertTrue(view.planner().revealableFacts().isEmpty());
        assertTrue(view.resolved().outcomes().contains("judgment"));
        assertFalse(view.writer().toString().contains("private reasoning"));
    }

    @Test
    void endpoint_requires_token_and_is_disabled_by_default() {
        UUID turnId = UUID.randomUUID();
        var service = mock(RuntimeTurnDiagnosticsApplicationService.class);
        var controller = new RuntimeTurnDiagnosticsController(service, false, new ApiRequestGuard("secret"));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.read(turnId, "wrong"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> controller.read(turnId, "secret"));
    }

    @Test
    void projects_resolved_artifact_without_future_facts_or_writer_mutation() {
        RuntimeTurnRepository repository = mock(RuntimeTurnRepository.class);
        RuntimeTurn turn = mock(RuntimeTurn.class);
        UUID turnId = UUID.randomUUID();
        RuntimePlan plan = new RuntimePlan("scene", "npc", "judgment", "private draft",
                mock(ActiveSourceContext.class), List.of(), List.of(), "provider", "model", "private reasoning");
        ResolvedTurnPlan resolved = ResolvedTurnPlan.of(
                new TurnPlan("visible scene", "visible npc", "resolved judgment", List.of("known fact"),
                        List.of("future secret")), List.of("door opened"));
        when(repository.findByTurnId(turnId)).thenReturn(java.util.Optional.of(turn));
        when(turn.turnId()).thenReturn(turnId);
        when(turn.commandId()).thenReturn(UUID.randomUUID());
        when(turn.adventureId()).thenReturn(new AdventureId(UUID.randomUUID()));
        when(turn.sessionId()).thenReturn(UUID.randomUUID());
        when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED);
        when(turn.committed()).thenReturn(false);
        when(turn.origin()).thenReturn(RuntimeTurnOrigin.PLAYER);
        when(turn.plan()).thenReturn(plan);
        when(turn.resolvedArtifact()).thenReturn(resolved);

        RuntimeTurnDiagnosticsView view = new RuntimeTurnDiagnosticsApplicationService(repository).readByTurnId(turnId).orElseThrow();

        assertEquals(List.of("known fact"), view.planner().revealableFacts());
        assertEquals(List.of("known fact"), view.writer().revealableFacts());
        assertEquals("", view.writer().prose());
        assertFalse(view.writer().toString().contains("future secret"));
        assertFalse(view.writer().toString().contains("private reasoning"));
    }

    @Test
    void exposes_architecture_diagnostics_route_and_legacy_alias() throws Exception {
        RequestMapping controllerMapping = RuntimeTurnDiagnosticsController.class.getAnnotation(RequestMapping.class);
        assertTrue(java.util.Arrays.asList(controllerMapping.value()).contains("/internal/v1/runtime-turns"));
        GetMapping turnMapping = RuntimeTurnDiagnosticsController.class.getDeclaredMethod(
                "read", UUID.class, String.class).getAnnotation(GetMapping.class);
        assertTrue(java.util.Arrays.asList(turnMapping.value()).contains("/{turnId}/diagnostics"));
        assertTrue(java.util.Arrays.asList(turnMapping.value()).contains("/turns/{turnId}"));
    }

    @Test
    void maps_compatibility_and_storage_failures_without_exposing_payload_details() {
        var handler = new ScenarioExceptionHandler();
        var compatibility = handler.runtimeTurnCompatibility(new RuntimeTurnCompatibilityException("secret payload", null));
        var storage = handler.runtimeTurnPersistence(new RuntimeTurnPersistenceException("db detail", new IllegalStateException("secret")));

        assertEquals(422, compatibility.getStatusCode().value());
        assertEquals("RUNTIME_TURN_COMPATIBILITY_ERROR", compatibility.getBody().get("error"));
        assertFalse(compatibility.getBody().toString().contains("secret"));
        assertEquals(503, storage.getStatusCode().value());
        assertEquals("RUNTIME_TURN_STORAGE_UNAVAILABLE", storage.getBody().get("error"));
        assertFalse(storage.getBody().toString().contains("secret"));
    }
}
