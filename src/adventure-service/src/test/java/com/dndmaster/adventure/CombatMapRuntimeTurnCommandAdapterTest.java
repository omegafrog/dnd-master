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
                        CombatMapMovementStatus.RETRY_WAIT, traversed, traversed.getLast(), List.of(), null);
            }
        };

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper())
                .execute(validCommand());

        assertEquals(RuntimeTurnCommandExecution.Status.TRANSIENT_FAILURE, result.status());
        assertEquals(CombatMapMovementStatus.RETRY_WAIT, result.movementResult().status());
        assertEquals(traversed, result.movementResult().traversedPath());
        org.junit.jupiter.api.Assertions.assertTrue(result.value().contains("\"status\":\"RETRY_WAIT\""));
        org.junit.jupiter.api.Assertions.assertTrue(result.value().contains(operationId.toString()));
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
