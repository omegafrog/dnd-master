package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainValidationTest {
    @Test
    void gridPositionRejectsNegativeCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new GridPosition(-1, 0));
        assertEquals(2, new GridPosition(2, 3).x());
    }

    @Test
    void explorationTokenCountIsOwnedByTheMapAndUsesItsCurrentPosition() {
        PlayerId owner = new PlayerId(UUID.randomUUID());
        GridPosition position = new GridPosition(1, 1);
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(4, 4, 50, 5), owner,
                List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER, position,
                        TokenController.PLAYER, owner)), Set.of(), List.of(), 0, null);
        map.refreshVisibility(0);

        List<CombatToken> first = map.ensurePlayerTokenCount(owner, 2);
        List<CombatToken> replay = map.ensurePlayerTokenCount(owner, 2);

        assertEquals(2, first.size());
        assertEquals(position, first.get(1).position());
        assertEquals(first.stream().map(CombatToken::id).toList(), replay.stream().map(CombatToken::id).toList());
        assertThrows(CombatMapMovementDeniedException.class,
                () -> map.ensurePlayerTokenCount(new PlayerId(UUID.randomUUID()), 2));
        assertThrows(CombatMapMovementDeniedException.class, () -> map.ensurePlayerTokenCount(owner, 0));
    }
}
