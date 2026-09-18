package com.dndmaster.combatmap.application.movement;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.SpatialFeature;
import com.dndmaster.combatmap.domain.SpatialFeatureVisibility;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.Optional;

/** Resolution seam for a known normal stop based on already-public map state. */
@FunctionalInterface
public interface MovementInterruptionPolicy {
    Optional<MovementInterruption> beforeEnter(MovementResolutionOperation operation, GridPosition nextCell);

    /**
     * Supplies the current map only to policies that need an existing public
     * state seam. The two-argument method remains the compatibility seam for
     * callers that provide an explicit policy.
     */
    default Optional<MovementInterruption> beforeEnter(MovementResolutionOperation operation, GridPosition nextCell,
            CombatMap map) {
        return beforeEnter(operation, nextCell);
    }

    static MovementInterruptionPolicy never() {
        return (operation, nextCell) -> Optional.empty();
    }

    /**
     * Stops before entering a public spatial feature that already declares an
     * enter-cell reaction. This consumes no hidden information and performs no
     * detection or rule judgment; those belong to the later movement plans.
     */
    static MovementInterruptionPolicy publicSpatialFeatures() {
        return new MovementInterruptionPolicy() {
            @Override
            public Optional<MovementInterruption> beforeEnter(MovementResolutionOperation operation, GridPosition nextCell) {
                return Optional.empty();
            }

            @Override
            public Optional<MovementInterruption> beforeEnter(MovementResolutionOperation operation, GridPosition nextCell,
                    CombatMap map) {
                boolean publicReaction = map.spatialFeatures().stream()
                        .filter(feature -> feature.visibility() != SpatialFeatureVisibility.HIDDEN)
                        .filter(feature -> feature.cells().contains(nextCell))
                        .filter(feature -> feature.triggers().contains(SpatialTrigger.ENTER_CELL))
                        .anyMatch(MovementInterruptionPolicy::canInterrupt);
                return publicReaction
                        ? Optional.of(new MovementInterruption("SPATIAL_FEATURE_REQUIRES_DECISION",
                                java.util.List.of("SPATIAL_FEATURE_REQUIRES_DECISION")))
                        : Optional.empty();
            }
        };
    }

    private static boolean canInterrupt(SpatialFeature feature) {
        return switch (feature.state()) {
            case DISARMED, RESOLVED, ENDED, OPEN -> false;
            default -> true;
        };
    }
}
