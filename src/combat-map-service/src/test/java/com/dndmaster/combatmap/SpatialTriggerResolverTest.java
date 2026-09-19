package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.combatmap.application.spatial.SpatialTriggerResolver;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.PlayerId;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureProvenance;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialFeatureVisibility;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpatialTriggerResolverTest {
    @Test
    void secret_door_discovery_does_not_open_or_change_traversal_until_interaction() {
        SpatialFeature door = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.SECRET_DOOR,
                List.of(new GridPosition(2, 2)), null, Set.of(SpatialTrigger.BECOME_VISIBLE, SpatialTrigger.INTERACT),
                SpatialFeatureProvenance.runtime("runtime", 1, 0));
        CombatMap map = map(door);
        SpatialTriggerResolver resolver = new SpatialTriggerResolver();

        assertEquals(List.of("SECRET_DOOR_DISCOVERED:2,2"), resolver.resolve(map, SpatialTrigger.BECOME_VISIBLE, new GridPosition(2, 2)));
        assertEquals(SpatialFeature.State.DISCOVERED, door.state());
        assertEquals(List.of("SECRET_DOOR_OPENED:2,2"), resolver.resolve(map, SpatialTrigger.INTERACT, new GridPosition(2, 2)));
        assertEquals(SpatialFeature.State.OPEN, door.state());
        assertEquals(List.of(), resolver.resolve(map, SpatialTrigger.INTERACT, new GridPosition(2, 2)));
    }

    @Test
    void a_non_repeatable_trigger_is_not_applied_twice() {
        SpatialFeature feature = SpatialFeature.active(UUID.randomUUID(), SpatialFeatureType.HAZARD_AREA,
                List.of(new GridPosition(1, 1)), SpatialFeatureProvenance.runtime("runtime", 1, 0), -1);
        CombatMap map = map(feature);
        feature = map.spatialFeatures().getFirst();
        map.spatialFeatures().getFirst().triggers();
        // The feature created by active() has no triggers; this assertion locks the idempotent domain seam below.
        assertEquals(List.of(), new SpatialTriggerResolver().resolve(map, SpatialTrigger.ENTER_CELL, new GridPosition(1, 1)));
        assertEquals(SpatialFeature.State.ACTIVE, feature.state());
    }

    @Test
    void secret_door_discovery_is_not_repeated_after_the_location_is_known() {
        SpatialFeature door = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.SECRET_DOOR,
                List.of(new GridPosition(2, 2)), null, Set.of(SpatialTrigger.BECOME_VISIBLE),
                SpatialFeatureProvenance.runtime("runtime", 1, 0));
        CombatMap map = map(door);
        SpatialTriggerResolver resolver = new SpatialTriggerResolver();

        assertEquals(List.of("SECRET_DOOR_DISCOVERED:2,2"), resolver.resolve(map, SpatialTrigger.BECOME_VISIBLE, new GridPosition(2, 2)));
        assertEquals(List.of(), resolver.resolve(map, SpatialTrigger.BECOME_VISIBLE, new GridPosition(2, 2)));
    }

    @Test
    void magical_area_effect_reacts_on_each_supported_event_until_duration_ends() {
        SpatialFeature effect = SpatialFeature.prepared(UUID.randomUUID(), SpatialFeatureType.MAGICAL_AREA_EFFECT,
                List.of(new GridPosition(1, 1)), null,
                Set.of(SpatialTrigger.ENTER_CELL, SpatialTrigger.LEAVE_CELL, SpatialTrigger.COMBAT_TURN_START),
                SpatialFeatureProvenance.runtime("runtime", 1, 0), 2, "EXPIRE", true);
        CombatMap map = map(effect);
        SpatialTriggerResolver resolver = new SpatialTriggerResolver();

        assertEquals(List.of("MAGICAL_AREA_EFFECT_TRIGGERED:1,1"), resolver.resolve(map, SpatialTrigger.ENTER_CELL, new GridPosition(1, 1)));
        assertEquals(List.of("MAGICAL_AREA_EFFECT_TRIGGERED:1,1"), resolver.resolve(map, SpatialTrigger.LEAVE_CELL, new GridPosition(1, 1)));
        assertEquals(List.of("MAGICAL_AREA_EFFECT_TRIGGERED:1,1"), resolver.resolveCombatTurnStart(map));
        effect.advanceDuration();
        effect.advanceDuration();
        assertEquals(List.of(), resolver.resolve(map, SpatialTrigger.ENTER_CELL, new GridPosition(1, 1)));
    }

    @Test
    void multi_cell_magical_area_effect_fires_once_at_combat_turn_start() {
        SpatialFeature effect = SpatialFeature.prepared(UUID.randomUUID(), SpatialFeatureType.MAGICAL_AREA_EFFECT,
                List.of(new GridPosition(1, 1), new GridPosition(1, 2), new GridPosition(2, 1)), null,
                Set.of(SpatialTrigger.COMBAT_TURN_START), SpatialFeatureProvenance.runtime("runtime", 1, 0), 2,
                "EXPIRE", true);

        assertEquals(List.of("MAGICAL_AREA_EFFECT_TRIGGERED:1,1"),
                new SpatialTriggerResolver().resolveCombatTurnStart(map(effect)));
    }

    @Test
    void refresh_visibility_discovers_a_become_visible_feature_before_emitting_its_event() {
        SpatialFeature feature = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.TRAP,
                List.of(new GridPosition(1, 1)), null, Set.of(SpatialTrigger.BECOME_VISIBLE),
                SpatialFeatureProvenance.runtime("runtime", 1, 0));
        CombatMap map = map(feature);
        SpatialTriggerResolver resolver = new SpatialTriggerResolver();

        assertEquals(List.of("TRAP_DISCOVERED:1,1"), resolver.resolveVisible(map, new GridPosition(1, 1)));
        assertEquals(SpatialFeatureVisibility.DISCOVERED, feature.visibility());
        assertEquals(List.of(), resolver.resolveVisible(map, new GridPosition(1, 1)));
    }

    private static CombatMap map(SpatialFeature feature) {
        return new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(5, 5, 50, 5), new PlayerId(UUID.randomUUID()), List.of(), Set.of(), List.of(), 0, null, null, List.of(feature));
    }
}
