package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
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
}
