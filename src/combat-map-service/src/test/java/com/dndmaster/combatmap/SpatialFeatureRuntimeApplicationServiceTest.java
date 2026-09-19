package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.application.spatial.SpatialFeatureRuntimeApplicationService;
import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
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
import com.dndmaster.combatmap.domain.TokenDiscovery;
import com.dndmaster.combatmap.domain.TokenId;
import com.dndmaster.combatmap.domain.TokenType;
import com.dndmaster.combatmap.domain.VisibilitySnapshot;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpatialFeatureRuntimeApplicationServiceTest {
    @Test
    void exposes_only_owned_visible_actions_and_replays_without_duplicate_public_events() {
        UUID ownerId = UUID.randomUUID();
        MapOwnerId owner = new MapOwnerId(ownerId);
        TokenId tokenId = new TokenId(UUID.randomUUID());
        GridPosition target = new GridPosition(2, 1);
        SpatialFeature feature = SpatialFeature.hidden(UUID.randomUUID(), SpatialFeatureType.TRAP, List.of(target),
                DetectionSpec.passive("perception", 12), Set.of(SpatialTrigger.INTERACT),
                SpatialFeatureProvenance.storyPlan("story", 0, 0));
        CombatMap map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(4, 3, 50, 5), new PlayerId(ownerId),
                List.of(new CombatToken(tokenId, TokenType.PLAYER, new GridPosition(1, 1), TokenController.PLAYER,
                        new PlayerId(ownerId), TokenDiscovery.REVEALED)), Set.of(), List.of(), 0, null, null, List.of(feature));
        map.replaceVisibility(new VisibilitySnapshot(Set.of(new GridPosition(1, 1), target),
                Set.of(new GridPosition(1, 1), target), Set.of(), List.of(), 0));
        InMemoryStore store = new InMemoryStore(owner, map);
        SpatialFeatureRuntimeApplicationService service = new SpatialFeatureRuntimeApplicationService(store);
        UUID commandId = UUID.randomUUID();

        var first = service.interact(map.id(), owner, tokenId, target, 0, commandId);
        var replay = service.interact(map.id(), owner, tokenId, target, 1, commandId);

        assertEquals(1, first.mapVersion());
        assertEquals(List.of("TRAP_INTERACTED:2,1"), first.publicEvents());
        assertEquals(1, replay.mapVersion());
        assertEquals(List.of(), replay.publicEvents());
        assertEquals(SpatialFeatureVisibility.DISCOVERED, feature.visibility());
        assertThrows(IllegalArgumentException.class, () -> service.interact(map.id(), owner, tokenId,
                new GridPosition(3, 2), 1, UUID.randomUUID()));
        assertThrows(RuntimeException.class, () -> service.interact(map.id(), new MapOwnerId(UUID.randomUUID()),
                tokenId, target, 1, UUID.randomUUID()));
    }

    private static final class InMemoryStore implements CombatMapViewStore {
        private final MapOwnerId owner;
        private CombatMap map;
        private final java.util.Map<UUID, VersionedOwnedCombatMap> history = new java.util.HashMap<>();

        private InMemoryStore(MapOwnerId owner, CombatMap map) { this.owner = owner; this.map = map; }
        @Override public void insert(MapOwnerId owner, CombatMap map) { this.map = map; }
        @Override public Optional<VersionedOwnedCombatMap> find(MapId id) { return Optional.of(new VersionedOwnedCombatMap(map, owner, map.version())); }
        @Override public Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId id, MapOwnerId owner) { return find(map.id()); }
        @Override public Optional<VersionedOwnedCombatMap> findByCommandId(UUID id) { return Optional.ofNullable(history.get(id)); }
        @Override public long update(MapOwnerId owner, CombatMap map, long expected) { return update(owner, map, expected, expected + 1, map.operationKey(), map.operationFingerprint()); }
        @Override public long update(MapOwnerId owner, CombatMap map, long expected, long persisted, UUID key, String fingerprint) {
            if (this.map.version() != expected) throw new IllegalStateException("stale");
            map.markPersisted(persisted, key, fingerprint);
            this.map = map;
            history.put(key, new VersionedOwnedCombatMap(map, owner, persisted));
            return persisted;
        }
    }
}
