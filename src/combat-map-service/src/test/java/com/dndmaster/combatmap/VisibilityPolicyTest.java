package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.combatmap.domain.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VisibilityPolicyTest {
    private final GridSpec grid = new GridSpec(7, 3, 50, 5);
    private final VisibilityPolicy policy = new VisibilityPolicy();

    @Test
    void wallAndClosedDoorBlockLineOfSightAndOpeningRevealsCell() {
        GridPosition origin = new GridPosition(1, 1);
        GridPosition target = new GridPosition(5, 1);
        VisibilitySnapshot closed = policy.calculate(grid, Set.of(origin), Set.of(new GridPosition(3, 1)),
                Set.of(new GridPosition(3, 1)), List.of(), Set.of(), 0);
        VisibilitySnapshot open = policy.calculate(grid, Set.of(origin), Set.of(), Set.of(), List.of(), Set.of(), 0);
        assertFalse(closed.current().contains(target));
        assertTrue(open.current().contains(target));
    }

    @Test
    void exploredCellsRemainDimAndHiddenTokenNeverEntersPlayerProjection() {
        GridPosition origin = new GridPosition(1, 1);
        CombatToken hidden = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.ENEMY,
                new GridPosition(5, 1), TokenController.AI_GAME_MASTER, null);
        VisibilitySnapshot snapshot = policy.calculate(grid, Set.of(origin), Set.of(new GridPosition(3, 1)),
                Set.of(new GridPosition(3, 1)), List.of(hidden), Set.of(), 0);
        assertTrue(snapshot.explored().contains(new GridPosition(3, 1)));
        assertFalse(snapshot.current().contains(new GridPosition(5, 1)));
        assertTrue(snapshot.observedTokens().isEmpty());
    }

    @Test
    void visibleTokenBecomesLastSeenForExactlyOneRuleTurn() {
        CombatToken enemy = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.ENEMY,
                new GridPosition(4, 1), TokenController.AI_GAME_MASTER, null);
        VisibilitySnapshot seen = policy.calculate(grid, Set.of(new GridPosition(1, 1)), Set.of(), Set.of(),
                List.of(enemy), Set.of(), 4);
        VisibilitySnapshot gone = policy.calculate(grid, Set.of(new GridPosition(1, 1)), Set.of(new GridPosition(3, 1)), Set.of(new GridPosition(3, 1)),
                List.of(enemy), seen.lastSeen(), 5);
        VisibilitySnapshot expired = policy.calculate(grid, Set.of(new GridPosition(1, 1)), Set.of(new GridPosition(3, 1)), Set.of(new GridPosition(3, 1)),
                List.of(enemy), gone.lastSeen(), 6);
        assertTrue(seen.observedTokens().contains(enemy.id()));
        assertTrue(gone.lastSeen().stream().anyMatch(last -> last.tokenId().equals(enemy.id()) && last.expiresAtTurn() == 6));
        assertTrue(expired.lastSeen().isEmpty());
    }
}
