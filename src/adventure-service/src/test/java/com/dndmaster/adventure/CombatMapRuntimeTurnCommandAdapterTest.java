package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatMapMoveCommand;
import com.dndmaster.adventure.application.combat.CombatMapMovementPreviewRejectedException;
import com.dndmaster.adventure.application.combat.CombatMapMovementStatus;
import com.dndmaster.adventure.application.combat.CombatMapPreviewPosition;
import com.dndmaster.adventure.application.runtime.CombatMapRuntimeTurnCommandAdapter;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CombatMapRuntimeTurnCommandAdapterTest {
    @Test
    void replays_the_durable_map_action_with_map_version_and_path_intact() {
        AtomicReference<CombatMapMoveCommand> received = new AtomicReference<>();
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult move(CombatMapMoveCommand command) {
                received.set(command);
                return new com.dndmaster.adventure.application.combat.CombatMapMoveResult(command.expectedVersion() + 1);
            }
        };
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        UUID ruleSetId = UUID.randomUUID();
        UUID sheetId = UUID.randomUUID();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), adventureId, sessionId,
                ownerId, "{\"ruleSetId\":\"" + ruleSetId + "\",\"characterSheetId\":\"" + sheetId
                        + "\",\"combatMapId\":\"" + mapId + "\",\"tokenId\":\"" + tokenId
                        + "\",\"expectedVersion\":4,\"distance\":10,\"appliedEdition\":\"DND_5E_2024\","
                        + "\"fingerprint\":\"preview-1\",\"waypoints\":[{\"x\":1,\"y\":1}]}", "combat-map.move",
                "{\"action\":\"MOVE\",\"path\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}]}", 0);

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper()).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.DONE, result.status());
        assertEquals(mapId, received.get().action().combatMapId());
        assertEquals(tokenId, received.get().action().tokenId());
        assertEquals(4, received.get().expectedVersion());
        assertEquals(10, received.get().distance());
        assertEquals("1,1;2,1", received.get().action().movementPath());
        assertEquals("DND_5E_2024", received.get().appliedEdition());
        assertEquals("preview-1", received.get().previewFingerprint());
        assertEquals(List.of(new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1)),
                received.get().waypoints());
    }

    @Test
    void keeps_a_stale_preview_failure_permanent_for_durable_turn_recovery() {
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult move(CombatMapMoveCommand command) {
                throw new CombatMapMovementPreviewRejectedException(409, "STALE_MOVEMENT_PROPOSAL");
            }
        };
        RuntimeTurnCommand command = RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{\"ruleSetId\":\"" + UUID.randomUUID()
                        + "\",\"characterSheetId\":\"" + UUID.randomUUID()
                        + "\",\"combatMapId\":\"" + UUID.randomUUID()
                        + "\",\"tokenId\":\"" + UUID.randomUUID()
                        + "\",\"expectedVersion\":4,\"distance\":10,\"appliedEdition\":\"DND_5E_2024\",\"fingerprint\":\"preview-1\",\"waypoints\":[]}",
                "combat-map.move", "{\"action\":\"MOVE\",\"path\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}]}", 0);

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper()).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
        assertEquals("STALE_MOVEMENT_PROPOSAL", result.value());
        assertEquals(409, result.movementConflict().httpStatus());
        assertEquals("STALE_MOVEMENT_PROPOSAL", result.movementConflict().code());
    }

    @Test
    void preserves_a_retry_wait_result_as_typed_durable_runtime_outcome() {
        UUID operationId = UUID.randomUUID();
        List<CombatMapPreviewPosition> traversed = List.of(
                new CombatMapPreviewPosition(1, 1), new CombatMapPreviewPosition(2, 1));
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult move(CombatMapMoveCommand command) {
                return new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                        CombatMapMovementStatus.RETRY_REQUIRED, traversed, traversed.getLast(), List.of(), null);
            }
        };

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper())
                .execute(validCommand());

        assertEquals(RuntimeTurnCommandExecution.Status.TRANSIENT_FAILURE, result.status());
        assertEquals(CombatMapMovementStatus.RETRY_REQUIRED, result.movementResult().status());
        assertEquals(traversed, result.movementResult().traversedPath());
        org.junit.jupiter.api.Assertions.assertTrue(result.value().contains("\"status\":\"RETRY_REQUIRED\""));
        org.junit.jupiter.api.Assertions.assertTrue(result.value().contains(operationId.toString()));
    }

    @Test
    void resumes_the_saved_map_operation_instead_of_restarting_the_command() {
        UUID operationId = UUID.randomUUID();
        AtomicReference<UUID> resumed = new AtomicReference<>();
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult move(CombatMapMoveCommand command) {
                throw new AssertionError("a saved retry must resume the durable operation");
            }
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult resumeMovementOperation(UUID mapId, UUID id) {
                resumed.set(id);
                return new com.dndmaster.adventure.application.combat.CombatMapMoveResult(5, id,
                        CombatMapMovementStatus.COMMITTED, List.of(),
                        new CombatMapPreviewPosition(2, 1), List.of(), null);
            }
        };
        String outcome = "{\"version\":4,\"operationId\":\"" + operationId
                + "\",\"status\":\"RETRY_REQUIRED\",\"requestedPath\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}],"
                + "\"traversedPath\":[{\"x\":1,\"y\":1}],\"finalPosition\":{\"x\":1,\"y\":1},\"publicEvents\":[]}";
        RuntimeTurnCommand command = validCommand().failed("RETRY_REQUIRED", outcome);

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper()).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.DONE, result.status());
        assertEquals(operationId, resumed.get());
        assertEquals(CombatMapMovementStatus.COMMITTED, result.movementResult().status());
    }

    @Test
    void reflects_a_saved_pending_check_without_resuming_without_a_player_result() {
        UUID operationId = UUID.randomUUID();
        AtomicReference<UUID> queried = new AtomicReference<>();
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult movementOperation(UUID mapId, UUID id) {
                queried.set(id);
                return new com.dndmaster.adventure.application.combat.CombatMapMoveResult(5, id,
                        CombatMapMovementStatus.CHECK_REQUIRED, List.of(), List.of(), null, List.of(), null);
            }
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult resumeMovementOperation(UUID mapId, UUID id) {
                throw new AssertionError("pending player check must not be resumed without its result");
            }
        };
        String outcome = "{\"version\":4,\"operationId\":\"" + operationId
                + "\",\"status\":\"CHECK_REQUIRED\",\"requestedPath\":[],\"traversedPath\":[],"
                + "\"publicEvents\":[]}";

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper())
                .execute(validCommand().failed("CHECK_REQUIRED", outcome));

        assertEquals(RuntimeTurnCommandExecution.Status.TRANSIENT_FAILURE, result.status());
        assertEquals(operationId, queried.get());
        assertEquals(CombatMapMovementStatus.CHECK_REQUIRED, result.movementResult().status());
    }

    @Test
    void preserves_requested_and_traversed_paths_and_interruption_status() {
        UUID operationId = UUID.randomUUID();
        List<CombatMapPreviewPosition> requested = List.of(
                new CombatMapPreviewPosition(1, 1), new CombatMapPreviewPosition(2, 1), new CombatMapPreviewPosition(3, 1));
        List<CombatMapPreviewPosition> traversed = requested.subList(0, 2);
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult move(CombatMapMoveCommand command) {
                return new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                        CombatMapMovementStatus.INTERRUPTED, requested, traversed, traversed.getLast(), List.of("FEATURE_REVEALED"), "FEATURE_REVEALED");
            }
        };

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper())
                .execute(validCommand());

        assertEquals(RuntimeTurnCommandExecution.Status.DONE, result.status());
        assertEquals(CombatMapMovementStatus.INTERRUPTED, result.movementResult().status());
        assertEquals(requested, result.movementResult().requestedPath());
        assertEquals(traversed, result.movementResult().traversedPath());
        assertEquals("FEATURE_REVEALED", result.movementResult().interruptionReason());
        assertEquals(operationId, result.movementResult().operationId());
    }

    private static RuntimeTurnCommand validCommand() {
        return RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{\"ruleSetId\":\"" + UUID.randomUUID()
                        + "\",\"characterSheetId\":\"" + UUID.randomUUID()
                        + "\",\"combatMapId\":\"" + UUID.randomUUID()
                        + "\",\"tokenId\":\"" + UUID.randomUUID()
                        + "\",\"expectedVersion\":3,\"distance\":5,\"appliedEdition\":\"DND_5E_2024\",\"fingerprint\":\"preview-1\",\"waypoints\":[]}",
                "combat-map.move", "{\"action\":\"MOVE\",\"path\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}]}", 0);
    }

    @Test
    void rejects_a_durable_move_without_preview_binding_fields() {
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult move(CombatMapMoveCommand command) {
                throw new AssertionError("invalid durable movement must not reach the map port");
            }
        };
        RuntimeTurnCommand command = RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{\"ruleSetId\":\"" + UUID.randomUUID()
                        + "\",\"characterSheetId\":\"" + UUID.randomUUID()
                        + "\",\"combatMapId\":\"" + UUID.randomUUID()
                        + "\",\"tokenId\":\"" + UUID.randomUUID()
                        + "\",\"expectedVersion\":0,\"distance\":5,\"appliedEdition\":\"DND_5E_2024\",\"waypoints\":[]}",
                "combat-map.move", "{\"action\":\"MOVE\",\"path\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}]}", 0);

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper()).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
    }

    @Test
    void rejects_a_durable_move_with_too_many_waypoints() {
        CombatMapPort mapPort = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand command) {}
            @Override public com.dndmaster.adventure.application.combat.CombatMapMoveResult move(CombatMapMoveCommand command) {
                throw new AssertionError("invalid durable movement must not reach the map port");
            }
        };
        String waypoints = java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> "{\"x\":1,\"y\":1}")
                .collect(java.util.stream.Collectors.joining(","));
        RuntimeTurnCommand command = RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "{\"ruleSetId\":\"" + UUID.randomUUID()
                        + "\",\"characterSheetId\":\"" + UUID.randomUUID()
                        + "\",\"combatMapId\":\"" + UUID.randomUUID()
                        + "\",\"tokenId\":\"" + UUID.randomUUID()
                        + "\",\"expectedVersion\":0,\"distance\":5,\"appliedEdition\":\"DND_5E_2024\",\"fingerprint\":\"preview-1\",\"waypoints\":[" + waypoints + "]}",
                "combat-map.move", "{\"action\":\"MOVE\",\"path\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}]}", 0);

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper()).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
    }
}
