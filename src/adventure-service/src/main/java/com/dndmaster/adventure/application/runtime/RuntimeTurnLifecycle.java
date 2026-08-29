package com.dndmaster.adventure.application.runtime;

/** Durable lifecycle of a runtime turn. Presentation is the commit boundary. */
public enum RuntimeTurnLifecycle {
    REQUESTED,
    PLANNING,
    RESOLVING,
    RESOLVED_UNCOMMITTED,
    WRITING,
    PRESENTATION_FAILED_RETRYABLE,
    PRESENTED;

    public boolean isCommitted() {
        return this == PRESENTED;
    }

    public boolean canTransitionTo(RuntimeTurnLifecycle next) {
        if (next == null || next == this) return false;
        return switch (this) {
            case REQUESTED -> next == PLANNING;
            case PLANNING -> next == RESOLVING;
            case RESOLVING -> next == RESOLVED_UNCOMMITTED;
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
