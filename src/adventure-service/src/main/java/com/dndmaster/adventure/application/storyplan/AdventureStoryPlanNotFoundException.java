package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.SessionId;

/** Raised when a story plan has not been persisted for a session yet. */
public final class AdventureStoryPlanNotFoundException extends RuntimeException {
    public AdventureStoryPlanNotFoundException(SessionId sessionId) {
        super("adventure story plan not found: " + sessionId.value());
    }
}
