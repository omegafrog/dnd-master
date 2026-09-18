package com.dndmaster.combatmap.application.movement;

public enum MovementOperationStatus {
    PREPARING, RETRY_WAIT, READY_TO_COMMIT, COMMITTED, CANCELLED;

    public boolean active() {
        return this == PREPARING || this == RETRY_WAIT || this == READY_TO_COMMIT;
    }
}
