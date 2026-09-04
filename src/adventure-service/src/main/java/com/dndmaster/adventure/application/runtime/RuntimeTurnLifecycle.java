package com.dndmaster.adventure.application.runtime;

/** Durable lifecycle of a runtime turn. Presentation is the commit boundary. */
public enum RuntimeTurnLifecycle {
    REQUESTED,
    PLANNING,
    PENDING_ROLL,
    RESOLVING,
    RESOLUTION_FIXED,
    NARRATING,
    SAFETY_CHECKING,
    READY_TO_COMMIT,
    COMMITTING,
    COMMITTED,
    DISCARDED,
    COMMIT_REPAIR_REQUIRED,
    RESOLVED_UNCOMMITTED,
    WRITING,
    PRESENTATION_FAILED_RETRYABLE,
    PRESENTED;

    public boolean isCommitted() {
        return this == PRESENTED || this == COMMITTED;
    }

    public boolean canTransitionTo(RuntimeTurnLifecycle next) {
        if (next == null || next == this) return false;
        return switch (this) {
            case REQUESTED -> next == PLANNING || next == RESOLVING;
            case PLANNING -> next == RESOLVING;
            case PENDING_ROLL -> next == RESOLVING;
            case RESOLVING -> next == RESOLVED_UNCOMMITTED || next == RESOLUTION_FIXED || next == PENDING_ROLL;
            case RESOLUTION_FIXED -> next == NARRATING || next == DISCARDED;
            case NARRATING -> next == SAFETY_CHECKING || next == PRESENTATION_FAILED_RETRYABLE || next == DISCARDED;
            case SAFETY_CHECKING -> next == NARRATING || next == READY_TO_COMMIT;
            case READY_TO_COMMIT -> next == COMMITTING;
            case COMMITTING -> next == COMMITTED || next == COMMIT_REPAIR_REQUIRED;
            case COMMITTED, DISCARDED, COMMIT_REPAIR_REQUIRED -> false;
            case RESOLVED_UNCOMMITTED -> next == WRITING || next == PRESENTED;
            case WRITING -> next == PRESENTED || next == PRESENTATION_FAILED_RETRYABLE;
            case PRESENTATION_FAILED_RETRYABLE -> next == WRITING;
            case PRESENTED -> false;
        };
    }

    public RuntimeTurnLifecycle transitionTo(RuntimeTurnLifecycle next) {
        if (!canTransitionTo(next)) throw new IllegalStateException("invalid runtime turn lifecycle transition: " + this + " -> " + next);
        return next;
    }
}
