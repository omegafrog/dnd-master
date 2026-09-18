package com.dndmaster.combatmap.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.dndmaster.combatmap.application.spatial.SpatialPreparationCommandConflictException;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationVersionConflictException;
import com.dndmaster.combatmap.application.movement.CombatMapMovementStaleException;
import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.view.CombatMapViewService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiContractExceptionHandlerTest {
    @Test
    void maps_invalid_internal_token_to_unauthorized_response() {
        var response = new ApiContractExceptionHandler().handle(
                new ApiRequestGuard.ApiContractException(401, "INVALID_SERVICE_TOKEN"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("INVALID_SERVICE_TOKEN", response.getBody().code());
    }

    @Test
    void maps_spatial_preparation_retries_to_typed_conflicts() {
        var handler = new ApiContractExceptionHandler();

        var command = handler.handle(new SpatialPreparationCommandConflictException());
        var version = handler.handle(new SpatialPreparationVersionConflictException());

        assertEquals(409, command.getStatusCode().value());
        assertEquals("SPATIAL_PREPARATION_COMMAND_CONFLICT", command.getBody().code());
        assertEquals(409, version.getStatusCode().value());
        assertEquals("SPATIAL_PREPARATION_VERSION_CONFLICT", version.getBody().code());
    }

    @Test
    void maps_stale_movement_preview_to_a_typed_conflict() {
        var response = new ApiContractExceptionHandler().handle(new CombatMapMovementStaleException());

        assertEquals(409, response.getStatusCode().value());
        assertEquals("STALE_MOVEMENT_PROPOSAL", response.getBody().code());
    }

    @Test
    void rejects_an_oversized_preview_at_the_api_boundary() {
        var controller = new CombatMapController(mock(CombatMapViewService.class),
                mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var waypoints = java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> new CombatMapController.PositionRequest(1, 1))
                .toList();

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(
                UUID.randomUUID(), "service-secret",
                new CombatMapController.MovementPreviewRequestBody(UUID.randomUUID(), UUID.randomUUID(),
                        new CombatMapController.PositionRequest(1, 1), waypoints, "DND_5E_2024", 0L)));
    }
}
