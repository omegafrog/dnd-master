package com.dndmaster.adventure.application.combat;

import java.util.Optional;
import java.util.UUID;

public interface CombatOperationRepository {
    Optional<CombatOperation> findById(UUID operationId);
    void save(CombatOperation operation);
}
