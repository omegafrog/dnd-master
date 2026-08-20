package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.combatmap.application.view.*;
import com.dndmaster.combatmap.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TacticalTriggerApplicationServiceTest {
    @Test
    void appliesAPlannedRewardThroughTheOwnedMapPersistencePath() {
        var owner = new MapOwnerId(UUID.randomUUID());
        var map = new CombatMap(new MapId(UUID.randomUUID()), new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(5, 5, 5, 5), List.of(new CombatToken(new TokenId(UUID.randomUUID()), TokenType.PLAYER,
                        new GridPosition(0, 0), TokenController.PLAYER, new PlayerId(owner.value()))), List.of(), List.of());
        var store = new Store();
        store.insert(owner, map);
        var maps = new CombatMapViewService(store, source -> new PreparedMapData(map.grid(), map.tokens(), map.obstacles(), map.layers()),
                description -> new PreparedMapData(map.grid(), map.tokens(), map.obstacles(), map.layers()));

        var updated = new TacticalTriggerApplicationService(maps).apply(map.id().value(), owner.value(), 0, UUID.randomUUID(),
                "reward", "REWARD", List.of(), "reward");

        assertEquals(1L, updated.version());
        assertEquals(List.of("RESOLVED_REWARD"), updated.layers().stream().map(MapLayer::type).toList());
        assertEquals(updated, store.find(map.id()).orElseThrow().map());
    }

    private static final class Store implements CombatMapViewStore {
        private VersionedOwnedCombatMap state;
        public void insert(MapOwnerId owner, CombatMap map) { state = new VersionedOwnedCombatMap(map, owner, map.version()); }
        public Optional<VersionedOwnedCombatMap> find(MapId id) { return Optional.ofNullable(state).filter(value -> value.map().id().equals(id)); }
        public Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId id, MapOwnerId owner) { return Optional.ofNullable(state).filter(value -> value.map().adventureId().equals(id) && value.owner().equals(owner)); }
        public Optional<VersionedOwnedCombatMap> findByCommandId(UUID id) { return Optional.ofNullable(state).filter(value -> id.equals(value.map().operationKey())); }
        public long update(MapOwnerId owner, CombatMap map, long expectedVersion) { return update(owner, map, expectedVersion, map.version(), map.operationKey(), map.operationFingerprint()); }
        public long update(MapOwnerId owner, CombatMap map, long expectedVersion, long persistedVersion, UUID operationKey, String fingerprint) {
            state = new VersionedOwnedCombatMap(map, owner, persistedVersion);
            return persistedVersion;
        }
    }
}
