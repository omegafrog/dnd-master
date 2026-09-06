package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.TurnResources;

/** Ensures a failed saga resumes its original command and reservation identity. */
public final class CombatOperationRecoveryPolicy {
    private CombatOperationRecoveryPolicy() {}

    public static CombatActionOperation resume(CombatActionOperation operation) {
        if (operation == null) throw new IllegalArgumentException("operation must not be null");
        if (operation.status() != CombatActionOperation.Status.PROCESSING_FAILED
                && operation.status() != CombatActionOperation.Status.RESERVED) {
            throw new IllegalStateException("combat operation is not resumable");
        }
        return operation;
    }

    public static TurnResources.Reservation reservationFor(CombatActionOperation operation) {
        return new TurnResources.Reservation(resume(operation).reservedCost());
    }
}
