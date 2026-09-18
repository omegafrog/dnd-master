package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.dndmaster.combatmap.api.ApiRequestGuard;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import com.dndmaster.combatmap.api.CombatMapController;
import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.MovementOperationResponse;
import com.dndmaster.combatmap.application.movement.MovementOperationStatus;
import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.application.spatial.SpatialFeatureApplicationService;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePlacementBatch;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationCommand;
import com.dndmaster.combatmap.domain.*;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.mockito.ArgumentCaptor;

class GmViewAuthorizationTest {
    @Test
    void starts_a_durable_movement_reservation_through_the_internal_boundary() {
        var movement = mock(CombatMapMovementService.class);
        var controller = new CombatMapController(mock(CombatMapViewService.class), movement, new ApiRequestGuard("service-secret"));
        UUID mapId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var request = new CombatMapController.MovementStartRequestBody(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new CombatMapController.PositionRequest(0, 0), new CombatMapController.PositionRequest(1, 0)),
                5, "DND_5E_2024", commandId, 0L, "fingerprint", "preview-fingerprint", List.of());
        when(movement.start(org.mockito.ArgumentMatchers.any())).thenReturn(
                new MovementOperationResponse(operationId, MovementOperationStatus.PREPARING, null));

        var response = controller.startMovement(mapId, "service-secret", commandId.toString(), request);

        assertEquals(operationId, response.operationId());
        assertEquals("PREPARING", response.status());
        verify(movement).start(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returns_a_typed_compatibility_move_state_when_the_reservation_is_not_terminal() {
        var movement = mock(CombatMapMovementService.class);
        var controller = new CombatMapController(mock(CombatMapViewService.class), movement, new ApiRequestGuard("service-secret"));
        UUID mapId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        var request = new CombatMapController.MoveRequest(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new CombatMapController.PositionRequest(0, 0), new CombatMapController.PositionRequest(1, 0)),
                5, "DND_5E_2024", commandId, 0L, "operation-fingerprint", "preview-fingerprint", List.of());
        when(movement.start(org.mockito.ArgumentMatchers.any())).thenReturn(
                new MovementOperationResponse(operationId, MovementOperationStatus.RETRY_WAIT, null));

        var response = controller.movePlayer(mapId, "service-secret", commandId.toString(), request);

        assertEquals(mapId, response.mapId());
        assertEquals(operationId, response.operationId());
        assertEquals("RETRY_WAIT", response.status());
        assertEquals("RETRY_REQUIRED", response.outcomeStatus());
    }

    @Test
    void rejects_a_staged_move_without_a_preview_fingerprint() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var request = new CombatMapController.MovementStartRequestBody(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new CombatMapController.PositionRequest(0, 0), new CombatMapController.PositionRequest(1, 0)),
                5, "DND_5E_2024", UUID.randomUUID(), 0L, "operation-fingerprint", null, List.of());

        var error = assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.startMovement(UUID.randomUUID(), "service-secret", request.commandId().toString(), request));

        assertEquals(400, error.status());
        assertEquals("MOVEMENT_PREVIEW_REQUIRED", error.code());
    }

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
    void activation_request_without_a_reviewed_draft_does_not_generate_a_new_map() {
        var maps = mock(CombatMapViewService.class);
        var controller = new CombatMapController(maps, mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var request = new CombatMapController.PrepareRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, "", "", null, null, null, null, null, 1);

        var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.prepare("service-secret", request));

        assertEquals(404, error.getStatusCode().value());
        verify(maps, never()).prepareGenerated(any(), any(), any(), any(MapGenerationRequest.class));
    }

    @Test
    void replays_a_preparation_command_before_attempting_map_generation() {
        var maps = mock(CombatMapViewService.class);
        var controller = new CombatMapController(maps, mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var request = new CombatMapController.PrepareRequest(adventureId, ownerId, UUID.randomUUID(), UUID.randomUUID(),
                "asset", "locator", null, null);
        var replay = new SpatialFeatureApplicationService.Result(new MapId(UUID.randomUUID()), 0,
                SpatialFeatureApplicationService.Status.READY, 0);
        when(maps.replaySpatialPreparation(eq(new AdventureId(adventureId)), eq(new MapOwnerId(ownerId)),
                any())).thenReturn(Optional.of(replay));

        var response = controller.prepare("service-secret", request);

        assertEquals(replay.mapId().value(), response.mapId());
        verify(maps).replaySpatialPreparation(eq(new AdventureId(adventureId)), eq(new MapOwnerId(ownerId)), any());
        verify(maps, never()).prepareGenerated(any(), any(), any(), any(MapGenerationRequest.class));
    }

    @Test
    void mapPreparationUsesGeometryDetectedFromTheSourceImageInsteadOfAFixedTwentyByTwentyGrid() {
        var maps = mock(CombatMapViewService.class);
        var preparedMap = new SpatialFeatureApplicationService.Result(new MapId(UUID.randomUUID()), 0,
                SpatialFeatureApplicationService.Status.READY, 0);
        when(maps.prepareGenerated(any(), any(), any(), any(MapGenerationRequest.class), anyBoolean(),
                any(SpatialFeaturePlacementBatch.class), anyLong(), any(SpatialPreparationCommand.class))).thenReturn(preparedMap);
        MapFilePreparationPort preparation = ignored -> new PreparedMapData(new GridSpec(13, 9, 16, 5), List.of(), Set.of(), List.of(
                new MapLayer("MAP_IMAGE", "data:image/png;base64,AAECAw==", LayerVisibility.PLAYER_VISIBLE),
                new MapLayer("GRID_BOUNDS", "7,11,208,144,240,180", LayerVisibility.PLAYER_VISIBLE)));
        var source = new MapImageEvidence("image/png", new byte[] {9, 8, 7});
        var controller = new CombatMapController(maps, mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"),
                (documentId, locator) -> Optional.of(source), null, null, preparation);
        var request = new CombatMapController.PrepareRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "cellar", "map.png", null, null, null, null, null, null, null, UUID.randomUUID(), 1L, 0,
                "맥주 저장고", "지하실", "계단을 내려갑니다", List.of(), List.of(), List.of(), UUID.randomUUID(), "map-image");

        controller.prepare("service-secret", request);

        var captured = ArgumentCaptor.forClass(MapGenerationRequest.class);
        verify(maps).prepareGenerated(any(), any(), any(), captured.capture(), eq(true),
                any(SpatialFeaturePlacementBatch.class), eq(0L), any(SpatialPreparationCommand.class));
        assertEquals(13, captured.getValue().gridWidth());
        assertEquals(9, captured.getValue().gridHeight());
        assertEquals(7, captured.getValue().gridOriginX());
        assertEquals(11, captured.getValue().gridOriginY());
        assertEquals(16, captured.getValue().gridCellSize());
        assertTrue(captured.getValue().gridConfirmed());
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
    void downloadsOnlyTheStoredPublicImageWithInternalAuthenticationAndNoStoreCaching() {
        UUID owner = UUID.randomUUID();
        MapId map = new MapId(UUID.randomUUID());
        var images = mock(PublicMapImageArtifactService.class);
        var artifact = new PublicMapImageArtifact(new MapOwnerId(owner), map, "image-version", 1, 2, new byte[] {1, 2, 3});
        when(images.download(map, new MapOwnerId(owner), artifact.reference())).thenReturn(java.util.Optional.of(artifact));
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class),
                new ApiRequestGuard("service-secret"), (documentId, locator) -> java.util.Optional.empty(),
                mock(MapGridAlignmentService.class), images);

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.alignmentImage(map.value(), owner, artifact.reference(), "wrong"));
        var response = controller.alignmentImage(map.value(), owner, artifact.reference(), "service-secret");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertArrayEquals(new byte[] {1, 2, 3}, response.getBody());
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

    @Test
    void rejects_a_move_without_command_id_before_dereferencing_the_request() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var request = new CombatMapController.MoveRequest(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new CombatMapController.PositionRequest(0, 0), new CombatMapController.PositionRequest(1, 0)),
                1, "DND_5E_2024", null, 0L, "preview", List.of());

        var error = assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.movePlayer(UUID.randomUUID(), "service-secret", request));
        assertEquals(400, error.status());
        assertEquals("INVALID_MAP_MOVE_PREVIEW", error.code());
    }

    @Test
    void rejects_a_blank_preview_fingerprint_as_a_typed_bad_request() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var request = new CombatMapController.MoveRequest(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new CombatMapController.PositionRequest(0, 0), new CombatMapController.PositionRequest(1, 0)),
                1, "DND_5E_2024", UUID.randomUUID(), 0L, "", List.of());

        var error = assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.movePlayer(UUID.randomUUID(), "service-secret", request));
        assertEquals(400, error.status());
        assertEquals("INVALID_MAP_MOVE_PREVIEW", error.code());
    }

    @Test
    void rejects_missing_numeric_confirmation_fields_as_a_typed_bad_request() {
        var controller = new CombatMapController(mock(CombatMapViewService.class), mock(CombatMapMovementService.class), new ApiRequestGuard("service-secret"));
        var request = new CombatMapController.MoveRequest(UUID.randomUUID(), UUID.randomUUID(),
                List.of(new CombatMapController.PositionRequest(null, 0), new CombatMapController.PositionRequest(1, 0)),
                1, "DND_5E_2024", UUID.randomUUID(), null, "preview", List.of());

        var error = assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.movePlayer(UUID.randomUUID(), "service-secret", request));
        assertEquals(400, error.status());
        assertEquals("INVALID_MAP_MOVE_PREVIEW", error.code());
    }
}
