package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnDiagnosticsApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnDiagnosticsApplicationService.RuntimeTurnDiagnosticsView;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.api.ApiRequestGuard;
import com.dndmaster.adventure.api.RuntimeTurnDiagnosticsController;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
}
