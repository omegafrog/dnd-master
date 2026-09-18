package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.GridPosition;
import java.util.Optional;

/** Resolution seam for a known normal stop; spatial detection policies arrive in a later plan. */
@FunctionalInterface
public interface MovementInterruptionPolicy {
    Optional<MovementInterruption> beforeEnter(MovementResolutionOperation operation, GridPosition nextCell);

    static MovementInterruptionPolicy never() {
        return (operation, nextCell) -> Optional.empty();
    }
}
