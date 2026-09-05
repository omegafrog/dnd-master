package com.dndmaster.adventure.application.combat;

import java.util.Optional;
import java.util.UUID;

public interface CombatActionOperationRepository {
    Optional<CombatActionOperation> findByCommandId(UUID commandId);
    void save(CombatActionOperation operation);

    default boolean hasPendingForEncounter(UUID encounterId) { return false; }
}
