package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureSessionTest {
    @Test
    void permits_pre_start_party_changes_but_rejects_party_over_storybook_limit() {
        AdventureSession session = AdventureSession.create(
                SessionId.generate(), new OwnerPlayerId(UUID.randomUUID()), UUID.randomUUID(), 1, 1);
        AdventurePartyMember first = new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT, true, false, true, false, true, false);

        session.addPartyMember(first);

        assertEquals(1, session.party().size());
        assertThrows(IllegalStateException.class, () -> session.addPartyMember(new AdventurePartyMember(
                new CharacterSheetId(UUID.randomUUID()), ControlMode.AGENT, true, true, true, true, true, true)));
        session.removePartyMember(first.characterSheetId());
        assertEquals(0, session.party().size());
    }
}
