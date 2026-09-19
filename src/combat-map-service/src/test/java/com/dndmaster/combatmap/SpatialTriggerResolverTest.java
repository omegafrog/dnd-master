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

        assertEquals(List.of(), resolver.resolve(map, SpatialTrigger.BECOME_VISIBLE, new GridPosition(2, 2)));
        assertEquals(SpatialFeature.State.HIDDEN, door.state());
        door.discover();
        assertEquals(List.of("SECRET_DOOR_OPENED:2,2"), resolver.resolve(map, SpatialTrigger.INTERACT, new GridPosition(2, 2)));
        assertEquals(SpatialFeature.State.OPEN, door.state());
        assertEquals(List.of(), resolver.resolve(map, SpatialTrigger.INTERACT, new GridPosition(2, 2)));
    }

    @Test
    void hidden_feature_cannot_be_interacted_with_before_discovery() {
        SpatialFeature door = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.SECRET_DOOR,
                List.of(new GridPosition(2, 2)), null, Set.of(SpatialTrigger.INTERACT),
                SpatialFeatureProvenance.runtime("runtime", 1, 0));
        CombatMap map = map(door);

        assertEquals(List.of(), new SpatialTriggerResolver().resolve(map, SpatialTrigger.INTERACT, new GridPosition(2, 2)));
        assertEquals(SpatialFeatureVisibility.HIDDEN, door.visibility());
        assertEquals(SpatialFeature.State.HIDDEN, door.state());
    }

    @Test
    void observation_discovers_but_does_not_trigger_a_hidden_feature() {
        SpatialFeature feature = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.TRAP,
                List.of(new GridPosition(1, 1)), null, Set.of(SpatialTrigger.OBSERVE),
                SpatialFeatureProvenance.runtime("runtime", 1, 0));
        CombatMap map = map(feature);

        assertEquals(List.of("TRAP_DISCOVERED:1,1"), new SpatialTriggerResolver().resolveObserved(
                map, SpatialTrigger.OBSERVE, new GridPosition(1, 1), Set.of(feature.id())));
        assertEquals(SpatialFeatureVisibility.DISCOVERED, feature.visibility());
        assertEquals(SpatialFeature.State.DISCOVERED, feature.state());
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

        assertEquals(List.of(), resolver.resolve(map, SpatialTrigger.BECOME_VISIBLE, new GridPosition(2, 2)));
        assertEquals(SpatialFeatureVisibility.HIDDEN, door.visibility());
        door.discover();
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
    void combat_turn_start_deduplicates_the_same_spatial_feature_id_but_keeps_distinct_effects() {
        UUID repeatedId = UUID.randomUUID();
        SpatialFeature firstRow = SpatialFeature.prepared(repeatedId, SpatialFeatureType.MAGICAL_AREA_EFFECT,
                List.of(new GridPosition(1, 1), new GridPosition(1, 2)), null,
                Set.of(SpatialTrigger.COMBAT_TURN_START), SpatialFeatureProvenance.runtime("runtime", 1, 0), 2,
                "EXPIRE", true, true);
        SpatialFeature overlappingRow = SpatialFeature.prepared(UUID.randomUUID(), SpatialFeatureType.MAGICAL_AREA_EFFECT,
                List.of(new GridPosition(2, 1), new GridPosition(2, 2)), null,
                Set.of(SpatialTrigger.COMBAT_TURN_START), SpatialFeatureProvenance.runtime("runtime", 1, 0), 2,
                "EXPIRE", true, true);
        SpatialFeature distinctEffect = SpatialFeature.prepared(UUID.randomUUID(), SpatialFeatureType.MAGICAL_AREA_EFFECT,
                List.of(new GridPosition(3, 1)), null,
                Set.of(SpatialTrigger.COMBAT_TURN_START), SpatialFeatureProvenance.runtime("runtime", 1, 0), 2,
                "EXPIRE", true, true);

        assertEquals(List.of("MAGICAL_AREA_EFFECT_TRIGGERED:1,1", "MAGICAL_AREA_EFFECT_TRIGGERED:2,1",
                        "MAGICAL_AREA_EFFECT_TRIGGERED:3,1"),
                new SpatialTriggerResolver().resolveCombatTurnStart(map(firstRow, overlappingRow, distinctEffect)));
    }

    @Test
    void visibility_refresh_does_not_discover_without_a_successful_check() {
        SpatialFeature feature = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.TRAP,
                List.of(new GridPosition(1, 1)), null, Set.of(SpatialTrigger.BECOME_VISIBLE),
                SpatialFeatureProvenance.runtime("runtime", 1, 0));
        CombatMap map = map(feature);
        SpatialTriggerResolver resolver = new SpatialTriggerResolver();

        assertEquals(List.of(), resolver.resolveVisible(map, new GridPosition(1, 1)));
        assertEquals(SpatialFeatureVisibility.HIDDEN, feature.visibility());
        assertEquals(List.of(), resolver.resolveVisible(map, new GridPosition(1, 1)));
    }

    @Test
    void resolves_each_multi_cell_become_visible_feature_once_across_all_new_cells() {
        UUID featureId = UUID.randomUUID();
        SpatialFeature feature = SpatialFeature.hidden(featureId, SpatialFeatureType.TRAP,
                List.of(new GridPosition(2, 2), new GridPosition(2, 3)), null,
                Set.of(SpatialTrigger.BECOME_VISIBLE), SpatialFeatureProvenance.runtime("runtime", 1, 0));
        CombatMap map = map(feature);

        assertEquals(List.of(),
                new SpatialTriggerResolver().resolveVisible(map,
                        List.of(new GridPosition(2, 2), new GridPosition(2, 3))));
        assertEquals(SpatialFeatureVisibility.HIDDEN, feature.visibility());
        assertEquals(SpatialFeature.State.HIDDEN, feature.state());
        assertEquals(List.of(), new SpatialTriggerResolver().resolveVisible(map,
                List.of(new GridPosition(2, 2), new GridPosition(2, 3))));
    }

    private static CombatMap map(SpatialFeature feature) {
        return map(List.of(feature));
    }

    private static CombatMap map(SpatialFeature... features) {
        return map(List.of(features));
    }

    private static CombatMap map(List<SpatialFeature> features) {
        return new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(5, 5, 50, 5), new PlayerId(UUID.randomUUID()), List.of(), Set.of(), List.of(), 0, null, null, features);
    }
}
