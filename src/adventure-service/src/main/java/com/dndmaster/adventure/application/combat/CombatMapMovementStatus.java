package com.dndmaster.adventure.application.combat;

/** Typed Adventure-side view of the Combat Map reservation lifecycle. */
public enum CombatMapMovementStatus {
    RETRY_REQUIRED,
    CHECK_REQUIRED,
    COMMITTED,
    INTERRUPTED,
    CANCELLED;

    public static CombatMapMovementStatus fromCombatMapStatus(String status) {
        return switch (status) {
            case "COMMITTED" -> COMMITTED;
            case "INTERRUPTED" -> INTERRUPTED;
            case "CHECK_REQUIRED" -> CHECK_REQUIRED;
            case "RETRY_REQUIRED" -> RETRY_REQUIRED;
            case "CANCELLED" -> CANCELLED;
            case "PREPARING", "RETRY_WAIT", "READY_TO_COMMIT" -> status.equals("PREPARING") || status.equals("READY_TO_COMMIT")
                    ? CHECK_REQUIRED : RETRY_REQUIRED;
            default -> throw new IllegalArgumentException("unknown combat map movement status: " + status);
        };
    }
}
