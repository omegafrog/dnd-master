package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureTurnCursorTest {
    @Test
    void advances_server_cursor_once_and_replays_same_composite_key_idempotently() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        CharacterSheetId agent = new CharacterSheetId(UUID.randomUUID());
        Adventure adventure = Adventure.create(
                AdventureId.generate(), SessionId.generate(), owner, new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), List.of(
                        new AdventurePartyMember(agent, ControlMode.AGENT, true, true, true, true, true, true),
                        new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, true, true, true, true, true)),
                new AdventureContext("start", null, null, null));
        UUID turnId = UUID.randomUUID();

        adventure.advanceTurn(owner, 0, agent, turnId);
        long version = adventure.version();
        adventure.advanceTurn(owner, 0, agent, turnId);

        assertEquals(1, adventure.turnIndex());
        assertEquals(version, adventure.version());
        assertThrows(IllegalStateException.class, () -> adventure.advanceTurn(owner, 0, agent, UUID.randomUUID()));
    }
}
