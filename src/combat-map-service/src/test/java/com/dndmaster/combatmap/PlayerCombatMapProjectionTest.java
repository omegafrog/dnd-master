package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.combatmap.api.PlayerCombatMapResponse;
import com.dndmaster.combatmap.application.view.CombatMapViewService;
import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.PlayerCombatMapView;
import com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.CombatToken;
import com.dndmaster.combatmap.domain.DetectionSpec;
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
import com.dndmaster.combatmap.domain.TokenController;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import com.dndmaster.combatmap.domain.VisibilitySnapshot;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerCombatMapProjectionTest {
    @Test
    void failed_detection_keeps_spatial_feature_out_of_player_map() {
        Fixture fixture = fixture(SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.SECRET_DOOR,
                List.of(new GridPosition(2, 1)), DetectionSpec.passive("perception", 15),
                Set.of(SpatialTrigger.INTERACT), SpatialFeatureProvenance.storyPlan("secret payload", 3, 1)));

        PlayerCombatMapResponse response = PlayerCombatMapResponse.from(fixture.service.displayForPlayer(fixture.map.id(), fixture.owner));

        assertTrue(response.spatialFeatures().isEmpty());
    }

    @Test
    void successful_discovery_projects_only_player_safe_location_state_and_interaction() {
        UUID featureId = UUID.randomUUID();
        SpatialFeature feature = SpatialFeature.hidden(featureId, SpatialFeatureType.SECRET_DOOR,
                List.of(new GridPosition(2, 1)), DetectionSpec.passive("perception", 15),
                Set.of(SpatialTrigger.INTERACT), SpatialFeatureProvenance.storyPlan("secret payload", 3, 1));
        feature.discover();
        Fixture fixture = fixture(feature);

        PlayerCombatMapView view = fixture.service.displayForPlayer(fixture.map.id(), fixture.owner);
        PlayerCombatMapResponse response = PlayerCombatMapResponse.from(view);

        assertEquals(1, response.spatialFeatures().size());
        PlayerCombatMapResponse.SpatialFeatureResponse projected = response.spatialFeatures().getFirst();
        assertEquals(featureId, projected.id());
        assertEquals("SECRET_DOOR", projected.type());
        assertEquals(List.of(new PlayerCombatMapResponse.PositionResponse(2, 1)), projected.cells());
        assertEquals("DISCOVERED", projected.visibility());
        assertEquals("DISCOVERED", projected.state());
        assertTrue(projected.interactable());
        assertFalse(response.toString().contains("perception"));
        assertFalse(response.toString().contains("secret payload"));
    }

    private static Fixture fixture(SpatialFeature feature) {
        UUID ownerId = UUID.randomUUID();
        MapOwnerId owner = new MapOwnerId(ownerId);
        CombatToken player = new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                new GridPosition(1, 1), TokenController.PLAYER, new PlayerId(ownerId));
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()), new GridSpec(4, 3, 50, 5), new PlayerId(ownerId),
                List.of(player), Set.of(), List.of(), 0, null, null, List.of(feature));
        map.replaceVisibility(new VisibilitySnapshot(Set.of(new GridPosition(1, 1), new GridPosition(2, 1)),
                Set.of(new GridPosition(1, 1), new GridPosition(2, 1)), Set.of(player.id()), List.of(), 0));
        InMemoryStore store = new InMemoryStore(owner, map);
        return new Fixture(owner, map, new CombatMapViewService(store, ignored -> {
            throw new AssertionError("not used");
        }, ignored -> {
            throw new AssertionError("not used");
        }));
    }

    private record Fixture(MapOwnerId owner, CombatMap map, CombatMapViewService service) {}

    private static final class InMemoryStore implements CombatMapViewStore {
        private final MapOwnerId owner;
        private CombatMap map;
        private final Map<UUID, VersionedOwnedCombatMap> history = new HashMap<>();

        private InMemoryStore(MapOwnerId owner, CombatMap map) {
            this.owner = owner;
            this.map = map;
        }

        @Override public void insert(MapOwnerId owner, CombatMap map) { this.map = map; }
        @Override public Optional<VersionedOwnedCombatMap> find(MapId id) { return Optional.of(new VersionedOwnedCombatMap(map, owner, map.version())); }
        @Override public Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId id, MapOwnerId owner) { return find(map.id()); }
        @Override public Optional<VersionedOwnedCombatMap> findByCommandId(UUID id) { return Optional.ofNullable(history.get(id)); }
        @Override public long update(MapOwnerId owner, CombatMap map, long expectedVersion) { return expectedVersion + 1; }
        @Override public long update(MapOwnerId owner, CombatMap map, long expectedVersion, long persistedVersion, UUID operationKey, String operationFingerprint) {
            this.map = map;
            history.put(operationKey, new VersionedOwnedCombatMap(map, owner, persistedVersion));
            return persistedVersion;
        }
    }
}
