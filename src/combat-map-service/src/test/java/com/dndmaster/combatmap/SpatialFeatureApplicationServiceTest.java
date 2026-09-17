package com.dndmaster.combatmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.combatmap.application.spatial.SpatialFeatureApplicationService;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePlacementProposal;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePreparationInput;
import com.dndmaster.combatmap.application.spatial.SpatialFeaturePreparationService;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationCommand;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationCommandConflictException;
import com.dndmaster.combatmap.application.spatial.SpatialPreparationVersionConflictException;
import com.dndmaster.combatmap.application.view.CombatMapViewStore;
import com.dndmaster.combatmap.application.view.MapOwnerId;
import com.dndmaster.combatmap.application.view.VersionedOwnedCombatMap;
import com.dndmaster.combatmap.domain.AdventureId;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.GridSpec;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import com.dndmaster.combatmap.domain.RuleSetId;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpatialFeatureApplicationServiceTest {
    @Test
    void replays_the_same_command_and_rejects_fingerprint_or_version_conflicts() {
        MapId mapId = new MapId(UUID.randomUUID());
        MapOwnerId owner = new MapOwnerId(UUID.randomUUID());
        InMemoryStore store = new InMemoryStore(owner, map(mapId, owner));
        SpatialFeaturePreparationService preparation = new SpatialFeaturePreparationService(context ->
                new SpatialFeaturePlacementProposal(List.of(new SpatialFeaturePlacementProposal.Candidate(
                        FEATURE_ID, SpatialFeatureType.TRAP, List.of(new GridPosition(2, 2)), true,
                        "storybook:page-4"))));
        SpatialFeatureApplicationService application = new SpatialFeatureApplicationService(store, preparation);
        SpatialFeaturePreparationInput input = input();
        UUID commandId = UUID.randomUUID();
        SpatialPreparationCommand command = new SpatialPreparationCommand(commandId, "fingerprint-1", 0);

        var first = application.prepare(mapId, owner, input, 1, command);
        var replay = application.prepare(mapId, owner, input, 1, command);

        assertEquals(1, first.version());
        assertEquals(first, replay);
        assertThrows(SpatialPreparationCommandConflictException.class,
                () -> application.prepare(mapId, owner, input, 1,
                        new SpatialPreparationCommand(commandId, "fingerprint-2", 0)));
        assertThrows(SpatialPreparationVersionConflictException.class,
                () -> application.prepare(mapId, owner, input, 1,
                        new SpatialPreparationCommand(UUID.randomUUID(), "fingerprint-3", 0)));
    }

    private static final UUID FEATURE_ID = UUID.randomUUID();

    private static SpatialFeaturePreparationInput input() {
        return new SpatialFeaturePreparationInput("story-plan:opening", List.of(
                new SpatialFeaturePreparationInput.Requirement(FEATURE_ID, SpatialFeatureType.TRAP, true,
                        java.util.Set.of("storybook:page-4"), java.util.Set.of(new GridPosition(2, 2)), null,
                        java.util.Set.of(SpatialTrigger.ENTER_CELL))));
    }

    private static CombatMap map(MapId id, MapOwnerId owner) {
        return new CombatMap(id, new AdventureId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new GridSpec(5, 5, 50, 5), new com.dndmaster.combatmap.domain.PlayerId(owner.value()),
                List.of(), java.util.Set.of(), List.of(), 0, null);
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
