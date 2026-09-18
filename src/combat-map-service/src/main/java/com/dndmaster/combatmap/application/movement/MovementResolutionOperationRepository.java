package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.MapId;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MovementResolutionOperationRepository {
    Optional<MovementResolutionOperation> findById(UUID operationId);
    Optional<MovementResolutionOperation> findOperationByCommandId(UUID commandId);
    Optional<MovementResolutionOperation> findActiveByMapId(MapId mapId);
    /** Returns either the newly reserved operation or an idempotent concurrent winner. */
    MovementResolutionOperation reserve(MovementResolutionOperation operation);
    void save(MovementResolutionOperation operation);
    List<MovementResolutionOperation> findRecoverable();
}
