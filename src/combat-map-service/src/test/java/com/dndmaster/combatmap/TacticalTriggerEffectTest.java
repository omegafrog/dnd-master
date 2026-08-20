package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.application.view.TacticalTriggerEffect;
import com.dndmaster.combatmap.domain.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalTriggerEffectTest {
    @Test
    void appliesOnlyPlannedEffectsAndRevealsTargetsRewardsAndFog() {
        var enemy = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.ENEMY, new GridPosition(2, 2), TokenController.AI_GAME_MASTER, null, TokenDiscovery.HIDDEN);
        var map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(5, 5, 5, 5), List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER, new GridPosition(0, 0), TokenController.PLAYER, new PlayerId(UUID.randomUUID())), enemy),
                List.of(), List.of(new MapLayer("INITIAL_FOG", "2,2", LayerVisibility.AI_ONLY)));

        var updated = map.apply(TacticalTriggerEffect.planned("reward", TacticalTriggerEffect.Kind.REWARD, List.of(enemy.id().value().toString())));
        updated = updated.apply(TacticalTriggerEffect.planned("reveal", TacticalTriggerEffect.Kind.FOG_REVEAL, List.of(enemy.id().value().toString())));

        assertEquals(TokenDiscovery.DISCOVERED, updated.tokens().get(1).discovery());
        assertEquals(List.of("RESOLVED_REWARD"), updated.layers().stream().map(MapLayer::type).toList());
    }

    @Test
    void rejectsUnplannedOrUnknownTargetEffects() {
        var map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(3, 3, 5, 5), List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER, new GridPosition(0, 0), TokenController.PLAYER, new PlayerId(UUID.randomUUID()))), List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> map.apply(TacticalTriggerEffect.unplanned("invented")));
        assertThrows(IllegalArgumentException.class, () -> map.apply(TacticalTriggerEffect.planned("bad", TacticalTriggerEffect.Kind.BOSS, List.of(UUID.randomUUID().toString()))));
    }

    @Test
    void appliesAuthoredNonUuidTargetIdsUsingTheMaterializationCanonicalId() {
        var enemy = new CombatToken(new TokenId(CombatMap.canonicalTokenId("enemy-1")), TokenType.ENEMY,
                new GridPosition(2, 2), TokenController.AI_GAME_MASTER, null, TokenDiscovery.HIDDEN);
        var map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(5, 5, 5, 5), List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                        new GridPosition(0, 0), TokenController.PLAYER, new PlayerId(UUID.randomUUID())), enemy), List.of(), List.of());

        var updated = map.apply(TacticalTriggerEffect.planned("reveal-enemy", TacticalTriggerEffect.Kind.FOG_REVEAL,
                List.of("enemy-1")));

        assertEquals(TokenDiscovery.DISCOVERED, updated.tokens().get(1).discovery());
    }

    @Test
    void materializesEveryPlannedRuntimeEffectIncludingTransitionsAndSurrender() {
        var map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(3, 3, 5, 5), List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                        new GridPosition(0, 0), TokenController.PLAYER, new PlayerId(UUID.randomUUID()))), List.of(), List.of());
        for (TacticalTriggerEffect.Kind kind : List.of(TacticalTriggerEffect.Kind.COMBAT_ENTRY, TacticalTriggerEffect.Kind.REINFORCEMENT,
                TacticalTriggerEffect.Kind.BOSS, TacticalTriggerEffect.Kind.SURRENDER)) {
            map = map.apply(TacticalTriggerEffect.planned(kind.name().toLowerCase(), kind, List.of()));
        }
        assertEquals(List.of("COMBAT_ENTRY", "REINFORCEMENT", "BOSS_TRANSITION", "SURRENDER"),
                map.layers().stream().map(MapLayer::type).toList());
        map = map.apply(TacticalTriggerEffect.planned("success", TacticalTriggerEffect.Kind.SUCCESS, List.of(), "ending-1"));
        assertTrue(map.layers().stream().anyMatch(layer -> layer.type().equals("TACTICAL_OUTCOME")));
        assertTrue(map.layers().stream().anyMatch(layer -> layer.type().equals("TACTICAL_TRANSITION") && layer.value().equals("ending-1")));
    }
}
