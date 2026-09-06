package com.dndmaster.adventure.domain.runtime.story;

/** Player-intent evidence that the current stage was left without solving its core problem. */
public record UnresolvedStageExit(String reason) {
    public UnresolvedStageExit {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("unresolved exit reason is required");
        reason = reason.trim();
    }
}
