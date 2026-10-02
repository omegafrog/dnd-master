package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.Adventure;

/** Saves a confirmed adventure and its durable compaction request in one local transaction. */
public interface AdventureConversationCompactionCommitPort {
    void saveConfirmedTurnAndRegister(Adventure adventure, ConversationCompactionJob job);
}
