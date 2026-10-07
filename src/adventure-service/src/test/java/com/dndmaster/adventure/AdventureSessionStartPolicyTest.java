package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureSessionStartPolicyTest {
    @Test
    void starts_with_one_direct_character_when_capacity_is_larger() {
        UUID packageId = UUID.randomUUID();
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), packageId, 1,
                6, new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()),
                        new RuleSetId(UUID.randomUUID()), List.of(), "ollama", List.of(), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT,
                true, true, true, true, true, true));

        assertDoesNotThrow(() -> session.beginStart(AdventureId.generate(), UUID.randomUUID()));
        assertEquals(AdventureSession.Status.STARTING, session.status());
    }

    @Test
    void still_requires_at_least_one_direct_character() {
        UUID packageId = UUID.randomUUID();
        var session = AdventureSession.create(SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), packageId, 1,
                6, new AdventureSessionRuntimeConfiguration(new ScenarioId(UUID.randomUUID()),
                        new RuleSetId(UUID.randomUUID()), List.of(), "ollama", List.of(), "opening"));
        session.addPartyMember(new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT,
                false, false, false, false, false, false));

        assertThrows(IllegalStateException.class,
                () -> session.beginStart(AdventureId.generate(), UUID.randomUUID()));
        assertEquals(AdventureSession.Status.DRAFT, session.status());
    }
}
