package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.runtime.CombatMapRuntimeTurnCommandAdapter;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CombatMapRuntimeTurnCommandAdapterTest {
    @Test
    void replays_the_durable_map_action_with_map_version_and_path_intact() {
        AtomicReference<CombatActionCommand> received = new AtomicReference<>();
        CombatMapPort mapPort = received::set;
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
                        + "\",\"expectedVersion\":4}", "combat-map.move",
                "{\"action\":\"MOVE\",\"path\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}]}", 0);

        RuntimeTurnCommandExecution result = new CombatMapRuntimeTurnCommandAdapter(mapPort, new ObjectMapper()).execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.DONE, result.status());
        assertEquals(mapId, received.get().combatMapId());
        assertEquals(tokenId, received.get().tokenId());
        assertEquals(4, received.get().expectedVersion());
        assertEquals("1,1;2,1", received.get().movementPath());
    }
}
