package com.dndmaster.adventure.domain.combat;

import java.util.Objects;

public record TurnResources(int movement, int movementSpeed, int movementUsed, boolean actionAvailable, boolean bonusActionAvailable,
                            boolean reactionAvailable) {
    public TurnResources(int movement, boolean actionAvailable, boolean bonusActionAvailable, boolean reactionAvailable) {
        this(movement, 30, Math.max(0, 30 - movement), actionAvailable, bonusActionAvailable, reactionAvailable);
    }
    public TurnResources(int movement, int movementSpeed, boolean actionAvailable, boolean bonusActionAvailable,
                         boolean reactionAvailable) {
        this(movement, movementSpeed, Math.max(0, movementSpeed - movement), actionAvailable,
                bonusActionAvailable, reactionAvailable);
    }
    public TurnResources {
        if (movement < 0 || movementSpeed < 0 || movementUsed < 0) {
            throw new IllegalArgumentException("movement values must be non-negative");
        }
    }

    public static TurnResources initial() {
        return initial(30);
    }

    public static TurnResources initial(int movementSpeed) {
        return new TurnResources(movementSpeed, movementSpeed, 0, true, true, true);
    }

    public int movementUsed() {
        return movementUsed;
    }

    public TurnResources withMovementSpeed(int speed) {
        if (speed < 0) throw new IllegalArgumentException("movement speed must be non-negative");
        int dashBonus = Math.max(0, movement + movementUsed - movementSpeed);
        return new TurnResources(Math.max(0, speed + dashBonus - movementUsed), speed, movementUsed,
                actionAvailable, bonusActionAvailable, reactionAvailable);
    }

    public TurnResources dash() {
        return new TurnResources(Math.addExact(movement, movementSpeed), movementSpeed, movementUsed,
                actionAvailable, bonusActionAvailable, reactionAvailable);
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
        return new TurnResources(movement - cost.movement(), movementSpeed, Math.addExact(movementUsed, cost.movement()),
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
