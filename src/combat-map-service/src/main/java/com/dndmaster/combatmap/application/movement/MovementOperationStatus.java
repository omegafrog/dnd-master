package com.dndmaster.combatmap.application.movement;

public enum MovementOperationStatus {
    PREPARING, CHECK_PENDING, RETRY_WAIT, READY_TO_COMMIT, COMMITTED, CANCELLED;

    public boolean active() {
        return this == PREPARING || this == CHECK_PENDING || this == RETRY_WAIT || this == READY_TO_COMMIT;
    }
}
