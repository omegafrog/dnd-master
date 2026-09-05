package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.CombatMapPlayerTokenResolver;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureStatus;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatMapPlayerTokenResolverTest {
    @Test
    void accepts_the_authoritative_player_token_even_when_legacy_maps_used_a_random_id() {
        UUID owner = UUID.randomUUID();
        UUID sheet = UUID.randomUUID();
        Adventure adventure = Adventure.rehydrate(AdventureId.generate(), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                List.of(new AdventurePartyMember(new CharacterSheetId(sheet), ControlMode.DIRECT, true, true, true, true, true, true)),
                List.of(), new AdventureContext("opening", null, null, null), AdventureStatus.ACTIVE, 0, 0, null);
        UUID mapToken = UUID.randomUUID();
        CombatMapViewPort view = (adventureId, ownerId) -> Optional.of(new CombatMapViewPort.View(UUID.randomUUID(),
                new CombatMapViewPort.Grid(3, 3, 50, 5), List.of(new CombatMapViewPort.Token(mapToken, "PLAYER", 0, 0)),
                List.of(), List.of(), List.of(), List.of(), 0));

        assertEquals(sheet, CombatMapPlayerTokenResolver.resolve(adventure, owner, mapToken, view).characterSheetId().value());
    }

    @Test
    void rejects_a_token_not_in_the_owned_player_projection() {
        UUID owner = UUID.randomUUID();
        UUID sheet = UUID.randomUUID();
        Adventure adventure = Adventure.rehydrate(AdventureId.generate(), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                List.of(new AdventurePartyMember(new CharacterSheetId(sheet), ControlMode.DIRECT, true, true, true, true, true, true)), List.of(),
                new AdventureContext("opening", null, null, null), AdventureStatus.ACTIVE, 0, 0, null);
        CombatMapViewPort empty = (adventureId, ownerId) -> Optional.empty();

        assertThrows(IllegalArgumentException.class,
                () -> CombatMapPlayerTokenResolver.resolve(adventure, owner, UUID.randomUUID(), empty));
    }
}
