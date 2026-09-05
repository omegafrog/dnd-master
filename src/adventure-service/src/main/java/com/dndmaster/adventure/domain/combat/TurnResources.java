package com.dndmaster.adventure.domain.combat;

import java.util.Objects;

public record TurnResources(int movement, boolean actionAvailable, boolean bonusActionAvailable,
                            boolean reactionAvailable) {
    public TurnResources {
        if (movement < 0) throw new IllegalArgumentException("movement must be non-negative");
    }

    public static TurnResources initial() {
        return new TurnResources(30, true, true, true);
    }

    /** Reserve is deliberately side-effect free; only commit consumes the cost. */
    public Reservation reserve(TurnResourceCost cost) {
        Objects.requireNonNull(cost, "cost must not be null");
        if (cost.movement() > movement
                || cost.action() && !actionAvailable
                || cost.bonusAction() && !bonusActionAvailable
                || cost.reaction() && !reactionAvailable) {
            throw new IllegalStateException("turn resources are insufficient");
        }
        return new Reservation(cost);
    }

    public TurnResources commit(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        TurnResourceCost cost = reservation.cost();
        if (cost.movement() > movement
                || cost.action() && !actionAvailable
                || cost.bonusAction() && !bonusActionAvailable
                || cost.reaction() && !reactionAvailable) {
            throw new IllegalStateException("turn resource reservation is no longer valid");
        }
        return new TurnResources(movement - cost.movement(),
                actionAvailable && !cost.action(),
                bonusActionAvailable && !cost.bonusAction(),
                reactionAvailable && !cost.reaction());
    }

    /** Releasing a reservation restores the unchanged value because reserve is side-effect free. */
    public TurnResources release(Reservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        return this;
    }

    public record Reservation(TurnResourceCost cost) {
        public Reservation {
            Objects.requireNonNull(cost, "cost must not be null");
        }
    }
}
