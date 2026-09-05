package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface CombatMapViewStore {
    void insert(MapOwnerId owner, CombatMap map);

    Optional<VersionedOwnedCombatMap> find(MapId id);
    Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId adventureId, MapOwnerId owner);
    Optional<VersionedOwnedCombatMap> findByCommandId(UUID commandId);

    default void activate(AdventureId adventureId, MapOwnerId owner, MapId mapId, int stagePosition) {
        throw new UnsupportedOperationException("map activation is not supported by this store");
    }

    long update(MapOwnerId owner, CombatMap map, long expectedVersion);

    long update(MapOwnerId owner, CombatMap map, long expectedVersion, long persistedVersion, UUID operationKey, String operationFingerprint);
}
