package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface CombatMapRepository {
    Optional<CombatMap> findById(MapId id);
    Optional<CombatMap> findByCommandId(UUID commandId);

    void save(CombatMap map);

    void save(CombatMap map, long persistedVersion, UUID operationKey, String operationFingerprint);

    /**
     * Commits the public map and the durable operation together when the
     * persistence adapter owns both records.  The default preserves small
     * in-memory adapters used by focused domain tests.
     */
    default void commitMovementResolution(CombatMap map, long persistedVersion,
            MovementResolutionOperation operation, MovementResolutionResult result) {
        save(map, persistedVersion, operation.commandId(), operation.fingerprint());
    }
}
