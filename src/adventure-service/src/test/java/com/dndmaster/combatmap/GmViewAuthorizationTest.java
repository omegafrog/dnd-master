package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.combatmap.api.ApiRequestGuard;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.dndmaster.combatmap.api.CombatMapController;
import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import java.util.List;
import java.util.Set;

class GmViewAuthorizationTest {
    @Test
    void rejectsUnauthenticatedAndWrongServiceRequestsButAllowsTheConfiguredInternalService() {
        var guard = new ApiRequestGuard("service-secret");
        var owner = UUID.randomUUID();

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> guard.internal(null));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> guard.internal("wrong-service"));
        assertDoesNotThrow(() -> guard.internal("service-secret"));
        assertDoesNotThrow(() -> guard.publicOwner("Bearer player", owner, owner));
    }

    @Test
    void protectsBothInternalPlayerViewsAndKeepsThePlayerSafeProjection() {
        var maps = mock(CombatMapViewService.class);
        var controller = new CombatMapController(maps, mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var owner = UUID.randomUUID();
        var adventure = UUID.randomUUID();
        var map = new MapId(UUID.randomUUID());
        var view = new PlayerCombatMapView(map, new GridSpec(5, 5, 5, 5), List.of(), Set.of(), List.of(),
                List.of(new MapLayer("visible", "value", LayerVisibility.PLAYER_VISIBLE)), Set.of(), Set.of(), Set.of(), 0);
        when(maps.displayForPlayer(any(), any())).thenReturn(view);
        when(maps.displayForAdventure(any(), any())).thenReturn(java.util.Optional.of(view));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.playerView(map.value(), owner, null));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.playerAdventureView(adventure, owner, "wrong"));
        assertNotNull(controller.playerView(map.value(), owner, "service-secret"));
        assertNotNull(controller.playerAdventureView(adventure, owner, "service-secret"));
        verify(maps).displayForPlayer(new MapId(map.value()), new MapOwnerId(owner));
    }

    @Test
    void reportsMalformedTriggerKindsAsBadRequest() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var request = new CombatMapController.TacticalTriggerRequest(UUID.randomUUID(), UUID.randomUUID(), 0, "entry", "NOT_A_TRIGGER", List.of());

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.applyTacticalTrigger(UUID.randomUUID(), "service-secret", request));

        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void protectsInternalMapPreparationEndpoints() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var request = new CombatMapController.PrepareRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "asset", "locator", 0, 0);

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.prepare(null, request));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.prepareUpload(null,
                new org.springframework.mock.web.MockMultipartFile("file", "map.png", "image/png", new byte[] {1}),
                request.adventureId(), request.ownerId(), request.ruleSetId()));
    }

    @Test
    void protectsAllInternalCombatMapMutators() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        UUID mapId = UUID.randomUUID();

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.movePlayer(mapId, null, null));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.controlAiState(mapId, null, null));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.changeDoor(mapId, null, null));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.reveal(mapId, null, null));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.gameTime(mapId, null, null));
    }

    @Test
    void rejectsNullBodiesAsBadRequestAfterInternalAuthorization() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        UUID mapId = UUID.randomUUID();

        assertEquals(400, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.applyTacticalTrigger(mapId, "service-secret", null)).getStatusCode().value());
        assertEquals(400, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.prepare("service-secret", null)).getStatusCode().value());
        assertEquals(400, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.movePlayer(mapId, "service-secret", null)).getStatusCode().value());
        assertEquals(400, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.controlAiState(mapId, "service-secret", null)).getStatusCode().value());
        assertEquals(400, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.changeDoor(mapId, "service-secret", null)).getStatusCode().value());
        assertEquals(400, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.reveal(mapId, "service-secret", null)).getStatusCode().value());
        assertEquals(400, assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.gameTime(mapId, "service-secret", null)).getStatusCode().value());
    }
}
