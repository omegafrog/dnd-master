package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.movement.HostileObservationResolver;
import com.dndmaster.combatmap.application.movement.HostileObservationResult;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostileObservationResolverTest {
    private final PlayerId player = new PlayerId(UUID.randomUUID());
    private final TokenId playerToken = new TokenId(UUID.randomUUID());
    private final TokenId enemyToken = new TokenId(UUID.randomUUID());

    @Test
    void blocked_line_of_sight_does_not_request_perception_or_stop_movement() {
        CombatMap map = map(new GridPosition(3, 0), List.of(new GridPosition(2, 0)));

        HostileObservationResult result = new HostileObservationResolver().evaluate(
                map, player, playerToken, new GridPosition(1, 0), UUID.randomUUID(), 1);

        assertEquals(HostileObservationResult.Status.NO_OBSERVATION, result.status());
        assertTrue(result.check().isEmpty());
        assertFalse(result.interruption().isPresent());
    }

    @Test
    void new_and_continuous_observation_are_distinguished() {
        CombatMap map = map(new GridPosition(3, 0), List.of());
        HostileObservationResolver resolver = new HostileObservationResolver();

        HostileObservationResult first = resolver.evaluate(
                map, player, playerToken, new GridPosition(1, 0), UUID.randomUUID(), 1);
        HostileObservationResult continuous = resolver.evaluate(
                map, player, playerToken, new GridPosition(1, 0), UUID.randomUUID(), 2);

        assertEquals(HostileObservationResult.Status.NEW, first.status());
        assertEquals("HOSTILE_OBSERVED", first.interruption().orElseThrow().reason());
        assertEquals(HostileObservationResult.Status.CONTINUOUS, continuous.status());
        assertTrue(continuous.interruption().isEmpty());
    }

    @Test
    void losing_sight_then_seeing_the_same_enemy_is_reacquisition() {
        CombatMap map = map(new GridPosition(3, 0), List.of());
        HostileObservationResolver resolver = new HostileObservationResolver();

        resolver.evaluate(map, player, playerToken, new GridPosition(1, 0), UUID.randomUUID(), 1);
        CombatMap blocked = map(new GridPosition(3, 0), List.of(new GridPosition(2, 0)));
        blocked.replaceHostileObservations(map.hostileObservations());
        resolver.evaluate(blocked, player, playerToken, new GridPosition(1, 0), UUID.randomUUID(), 2);
        map = map(new GridPosition(3, 0), List.of());
        map.replaceHostileObservations(blocked.hostileObservations());
        HostileObservationResult reacquired = resolver.evaluate(
                map, player, playerToken, new GridPosition(1, 0), UUID.randomUUID(), 3);

        assertEquals(HostileObservationResult.Status.REACQUIRED, reacquired.status());
        assertEquals("HOSTILE_OBSERVED", reacquired.interruption().orElseThrow().reason());
    }

    private CombatMap map(GridPosition enemyPosition, List<GridPosition> obstacles) {
        CombatToken playerValue = new CombatToken(playerToken, TokenType.PLAYER, new GridPosition(0, 0),
                TokenController.PLAYER, player);
        CombatToken enemyValue = new CombatToken(enemyToken, TokenType.ENEMY, enemyPosition,
                TokenController.AI_GAME_MASTER, null);
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(5, 3, 5, 5),
                player, List.of(playerValue, enemyValue), obstacles, List.of(), 0, null);
        map.refreshVisibility(0);
        return map;
    }
}
