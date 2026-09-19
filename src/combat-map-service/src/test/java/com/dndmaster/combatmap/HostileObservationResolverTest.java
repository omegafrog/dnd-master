package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.spatial.HostileObservationResolver;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenDiscovery;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostileObservationResolverTest {
    @Test
    void reports_new_hostile_only_when_line_of_sight_is_clear() {
        TokenId hostile = new TokenId(UUID.randomUUID());
        CombatMap map = map(new CombatToken(hostile, TokenType.ENEMY, new GridPosition(3, 1),
                TokenController.AI_GAME_MASTER, null, TokenDiscovery.DISCOVERED));

        HostileObservationResolver.Observation result = new HostileObservationResolver().resolve(map,
                new GridPosition(1, 1), Set.of());

        assertTrue(result.lineOfSight());
        assertTrue(result.newlyObserved());
    }

    @Test
    void does_not_reannounce_a_hostile_that_was_already_observed() {
        TokenId hostile = new TokenId(UUID.randomUUID());
        CombatMap map = map(new CombatToken(hostile, TokenType.ENEMY, new GridPosition(3, 1),
                TokenController.AI_GAME_MASTER, null, TokenDiscovery.DISCOVERED));

        HostileObservationResolver.Observation result = new HostileObservationResolver().resolve(map,
                new GridPosition(1, 1), Set.of(hostile));

        assertTrue(result.lineOfSight());
        assertFalse(result.newlyObserved());
    }

    @Test
    void ignores_hostile_hidden_behind_a_closed_door() {
        TokenId hostile = new TokenId(UUID.randomUUID());
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(5, 3, 50, 5), new PlayerId(UUID.randomUUID()),
                List.of(new CombatToken(hostile, TokenType.ENEMY, new GridPosition(3, 1),
                        TokenController.AI_GAME_MASTER, null, TokenDiscovery.DISCOVERED)),
                Set.of(), List.of(), 0, null, null, List.of());
        map.replaceDoors(Set.of(new com.dndmaster.combatmap.domain.Door(new GridPosition(2, 1), false)));

        HostileObservationResolver.Observation result = new HostileObservationResolver().resolve(map,
                new GridPosition(1, 1), Set.of());

        assertFalse(result.lineOfSight());
        assertFalse(result.newlyObserved());
    }

    private static CombatMap map(CombatToken hostile) {
        return new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(5, 3, 50, 5), new PlayerId(UUID.randomUUID()),
                List.of(hostile), Set.of(), List.of(), 0, null, null, List.of());
    }
}
