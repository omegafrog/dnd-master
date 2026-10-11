package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatTokenElevationTest {
    @Test
    void stores_nonnegative_numeric_elevation() {
        var token = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                new GridPosition(0, 0), TokenController.PLAYER, new PlayerId(UUID.randomUUID()), 10);

        assertEquals(10, token.elevation());
        assertThrows(IllegalArgumentException.class, () -> new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                new GridPosition(0, 0), TokenController.PLAYER, new PlayerId(UUID.randomUUID()), -1));
    }

    @Test
    void player_map_response_names_public_elevation_with_its_unit() {
        PlayerId owner = new PlayerId(UUID.randomUUID());
        CombatToken token = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                new GridPosition(0, 0), TokenController.PLAYER, owner, 15);
        var view = new com.dndmaster.combatmap.application.view.PlayerCombatMapView(
                new com.dndmaster.combatmap.domain.MapId(UUID.randomUUID()), new GridSpec(1, 1, 50, 5),
                java.util.List.of(token), java.util.Set.of(), java.util.List.of(), java.util.List.of(),
                java.util.Set.of(new GridPosition(0, 0)), java.util.Set.of(new GridPosition(0, 0)),
                java.util.Set.of(), 1);

        assertEquals(15, com.dndmaster.combatmap.api.PlayerCombatMapResponse.from(view)
                .tokens().getFirst().elevationFeet());
    }
}
