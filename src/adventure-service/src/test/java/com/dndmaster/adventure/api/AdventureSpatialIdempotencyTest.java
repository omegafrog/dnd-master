package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureSpatialIdempotencyTest {
    @Test
    void accepts_a_spatial_request_only_when_header_matches_command_id() {
        UUID commandId = UUID.randomUUID();
        AdventureController.SpatialActionRequest request = new AdventureController.SpatialActionRequest(
                UUID.randomUUID(), UUID.randomUUID(), 1, 2, 0L, commandId);

        assertDoesNotThrow(() -> AdventureController.requireSpatialIdempotencyKey(commandId, request));
        assertThrows(RuntimeException.class,
                () -> AdventureController.requireSpatialIdempotencyKey(UUID.randomUUID(), request));
        assertThrows(RuntimeException.class,
                () -> AdventureController.requireSpatialIdempotencyKey(null, request));
    }

    @Test
    void validates_turn_start_requests_with_the_same_header_policy() {
        UUID commandId = UUID.randomUUID();
        AdventureController.SpatialTurnRequest request = new AdventureController.SpatialTurnRequest(
                UUID.randomUUID(), 0L, commandId);

        assertDoesNotThrow(() -> AdventureController.requireSpatialIdempotencyKey(commandId, request));
        assertThrows(RuntimeException.class,
                () -> AdventureController.requireSpatialIdempotencyKey(UUID.randomUUID(), request));
    }

    @Test
    void rejects_a_spatial_roll_when_header_and_command_identity_differ() {
        UUID commandId = UUID.randomUUID();
        AdventureController.SpatialCheckRollRequest request = new AdventureController.SpatialCheckRollRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), commandId, 0L);

        assertDoesNotThrow(() -> AdventureController.requireSpatialIdempotencyKey(commandId, request));
        assertThrows(RuntimeException.class,
                () -> AdventureController.requireSpatialIdempotencyKey(UUID.randomUUID(), request));
    }

    @Test
    void requires_cancel_header_to_match_the_movement_operation_identity() {
        UUID operationId = UUID.randomUUID();

        assertDoesNotThrow(() -> AdventureController.requireMovementIdempotencyKey(operationId, operationId));
        assertThrows(RuntimeException.class,
                () -> AdventureController.requireMovementIdempotencyKey(UUID.randomUUID(), operationId));
        assertThrows(RuntimeException.class,
                () -> AdventureController.requireMovementIdempotencyKey(null, operationId));
    }
}
