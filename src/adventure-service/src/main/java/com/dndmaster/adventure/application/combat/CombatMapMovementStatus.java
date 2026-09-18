package com.dndmaster.adventure.application.combat;

/** Typed Adventure-side view of the Combat Map reservation lifecycle. */
public enum CombatMapMovementStatus {
    RETRY_REQUIRED,
    COMMITTED,
    CANCELLED;

    public static CombatMapMovementStatus fromCombatMapStatus(String status) {
        return switch (status) {
            case "COMMITTED" -> COMMITTED;
            case "CANCELLED" -> CANCELLED;
            case "PREPARING", "RETRY_WAIT", "READY_TO_COMMIT" -> RETRY_REQUIRED;
            default -> throw new IllegalArgumentException("unknown combat map movement status: " + status);
        };
    }
}
