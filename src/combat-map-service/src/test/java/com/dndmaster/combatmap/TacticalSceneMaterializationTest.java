package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.application.view.PreparedMapData;
import com.dndmaster.combatmap.application.view.TacticalSceneMaterialization;
import com.dndmaster.combatmap.application.view.PlayerSafeFogProjection;
import com.dndmaster.combatmap.domain.LayerVisibility;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.api.GmCombatMapResponse;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.TokenDiscovery;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TacticalSceneMaterializationTest {
    @Test
    void convertsNormalizedCoordinatesIntoCurrentGridAndKeepsHiddenTacticalDataInternal() {
        var scene = new TacticalSceneMaterialization(
                List.of(new TacticalSceneMaterialization.Placement("party", "PLAYER", .1, .2),
                        new TacticalSceneMaterialization.Placement("rat", "ENEMY", .9, .8)),
                List.of(new TacticalSceneMaterialization.Environment("barrel", "OBSTACLE", .5, .5)),
                List.of(new TacticalSceneMaterialization.Position(.9, .8)));

        PreparedMapData result = scene.materialize(new GridSpec(10, 10, 30, 5), UUID.randomUUID());

        assertEquals(1, result.tokens().getFirst().position().x());
        assertEquals(2, result.tokens().getFirst().position().y());
        assertEquals(TokenDiscovery.HIDDEN, result.tokens().get(1).discovery());
        assertEquals(1, result.obstacles().size());
        assertEquals("INITIAL_FOG", result.layers().getFirst().type());
    }

    @Test
    void rejectsNormalizedPlacementsThatCollideAfterGridConversion() {
        var scene = new TacticalSceneMaterialization(
                List.of(new TacticalSceneMaterialization.Placement("party", "PLAYER", .1, .1),
                        new TacticalSceneMaterialization.Placement("enemy", "ENEMY", .11, .11)), List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> scene.materialize(new GridSpec(10, 10, 30, 5), UUID.randomUUID()));
    }

    @Test
    void gmProjectionRetainsHiddenTokensAndFogThatPlayerProjectionMustNotReceive() {
        var scene = new TacticalSceneMaterialization(List.of(
                new TacticalSceneMaterialization.Placement("party", "PLAYER", .1, .1),
                new TacticalSceneMaterialization.Placement("rat", "ENEMY", .9, .9)), List.of(), List.of(new TacticalSceneMaterialization.Position(.9, .9)));
        var data = scene.materialize(new GridSpec(10, 10, 30, 5), UUID.randomUUID());
        var map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                data.grid(), data.tokens(), data.obstacles(), data.layers());

        var response = GmCombatMapResponse.from(map);

        assertEquals(2, response.tokens().size());
        assertEquals("INITIAL_FOG", response.layers().getFirst().type());
    }

    @Test
    void playerSafeProjectionRemovesInitialFogCoordinates() {
        var visible = PlayerSafeFogProjection.filter(Set.of(new GridPosition(1, 1), new GridPosition(9, 9)),
                List.of(new MapLayer("INITIAL_FOG", "9,9", LayerVisibility.AI_ONLY)));

        assertEquals(Set.of(new GridPosition(1, 1)), visible);
    }
}
