package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.combat.CombatEventRepository;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatEventReplayTest {
    @Test
    void replays_durable_player_safe_events_after_last_sequence() {
        var repository = new com.dndmaster.adventure.application.combat.InMemoryCombatEventRepository();
        UUID encounterId = UUID.randomUUID();
        repository.append(new CombatEvent(encounterId, 1, "COMBAT_STARTED", "{\"public\":true}"));
        repository.append(new CombatEvent(encounterId, 2, "TURN_STARTED", "{\"participantId\":\"hero\"}"));

        assertEquals(List.of(2L), repository.after(encounterId, 1).stream().map(CombatEvent::sequence).toList());
        assertEquals("{\"public\":true}", repository.after(encounterId, -1).get(0).playerPayload());
    }
}
