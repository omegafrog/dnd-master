package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

/** Shared resume contract used by an HTTP retry and the background recovery worker. */
@FunctionalInterface
public interface RuntimeTurnResumeClient {
    RuntimeTurnCommitOrchestrator.Result resume(UUID turnId, Runnable localAdventureCommit);
}
