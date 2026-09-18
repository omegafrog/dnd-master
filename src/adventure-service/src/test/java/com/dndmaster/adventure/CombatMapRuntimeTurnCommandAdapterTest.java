package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatMapMoveCommand;
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
}
