package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.Adventure;

/** Atomically persists a concluding adventure turn and completes its linked session. */
public interface AdventureCompletionCommitPort {
    void saveCompleted(Adventure adventure, ConversationCompactionJob compactionJob);
}
