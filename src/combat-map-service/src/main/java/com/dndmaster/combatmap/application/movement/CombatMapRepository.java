package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.*;
import java.util.Optional;
import java.util.UUID;

public interface CombatMapRepository {
    Optional<CombatMap> findById(MapId id);
    Optional<CombatMap> findByCommandId(UUID commandId);

    void save(CombatMap map);

    void save(CombatMap map, long persistedVersion, UUID operationKey, String operationFingerprint);
}
