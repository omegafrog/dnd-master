package com.dndmaster.adventure.domain.runtime.story;

/** Lifecycle of the currently materialized narrative stage. */
public enum StageLifecycle {
    ACTIVE,
    COMPLETED,
    EXITED_UNRESOLVED
}
