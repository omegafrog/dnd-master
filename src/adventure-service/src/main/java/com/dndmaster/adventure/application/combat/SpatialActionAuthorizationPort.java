package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.Objects;
import java.util.UUID;

/** Adventure Runtime boundary for player-owned spatial actions and their cost. */
@FunctionalInterface
public interface SpatialActionAuthorizationPort {
    void authorize(SpatialActionAuthorization command);

    record SpatialActionAuthorization(UUID adventureId, UUID actorId, String action, TurnResourceCost cost,
            UUID commandId, String fingerprint) {
        public SpatialActionAuthorization {
            Objects.requireNonNull(adventureId, "spatial action adventure must not be null");
            Objects.requireNonNull(actorId, "spatial action actor must not be null");
            Objects.requireNonNull(commandId, "spatial action command id must not be null");
            Objects.requireNonNull(fingerprint, "spatial action fingerprint must not be null");
            if (action == null || action.isBlank()) throw new IllegalArgumentException("spatial action must not be blank");
            Objects.requireNonNull(cost, "spatial action cost must not be null");
            action = action.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }

    /** Fail-closed fallback; the configured Adventure Runtime adapter owns resource reservation. */
    static SpatialActionAuthorizationPort requiredPlayerAction() {
        return command -> {
            if (!command.action().equals("OBSERVE") && !command.action().equals("INTERACT")) {
                throw new IllegalArgumentException("unsupported spatial action");
            }
            TurnResourceCost cost = command.cost();
            if (!cost.action() || cost.movement() != 0 || cost.bonusAction() || cost.reaction()) {
                throw new IllegalStateException("spatial action cost is not authorized");
            }
            throw new IllegalStateException("ACTIVE_COMBAT_REQUIRED");
        };
    }
}
