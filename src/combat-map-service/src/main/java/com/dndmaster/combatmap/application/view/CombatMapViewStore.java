package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface CombatMapViewStore {
    void insert(MapOwnerId owner, CombatMap map);

    Optional<VersionedOwnedCombatMap> find(MapId id);
    Optional<VersionedOwnedCombatMap> findByAdventureId(AdventureId adventureId, MapOwnerId owner);

    /** Removes the adventure's map from player views while it is in a mapless situation. */
    default void deactivateForAdventure(AdventureId adventureId, MapOwnerId owner) {
        throw new UnsupportedOperationException("map deactivation is not supported by this store");
    }

    /** 활성화 전 사용자가 편집 중인 맵 초안. */
    default Optional<VersionedOwnedCombatMap> findPreparedByAdventureId(AdventureId adventureId, MapOwnerId owner) {
        return Optional.empty();
    }
    default Optional<VersionedOwnedCombatMap> findByDefinition(AdventureId adventureId, MapOwnerId owner, UUID definitionId) {
        return java.util.stream.Stream.concat(findByAdventureId(adventureId, owner).stream(),
                findPreparedByAdventureId(adventureId, owner).stream())
                .filter(state -> state.map().layers().stream().anyMatch(layer ->
                        "MAP_DEFINITION_ID".equals(layer.type()) && definitionId.toString().equals(layer.value())))
                .findFirst();
    }

    default boolean wasActivated(AdventureId adventureId, MapOwnerId owner, MapId mapId) { return false; }

    Optional<VersionedOwnedCombatMap> findByCommandId(UUID commandId);

    default void activate(AdventureId adventureId, MapOwnerId owner, MapId mapId, int stagePosition) {
        throw new UnsupportedOperationException("map activation is not supported by this store");
    }

    default long activate(MapOwnerId owner, CombatMap map, long expectedVersion, int stagePosition,
            UUID operationKey, String operationFingerprint) {
        throw new UnsupportedOperationException("atomic map activation is not supported by this store");
    }

    long update(MapOwnerId owner, CombatMap map, long expectedVersion);

    long update(MapOwnerId owner, CombatMap map, long expectedVersion, long persistedVersion, UUID operationKey, String operationFingerprint);
}
