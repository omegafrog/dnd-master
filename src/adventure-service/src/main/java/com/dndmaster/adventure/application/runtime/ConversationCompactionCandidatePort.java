package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;

@FunctionalInterface
public interface ConversationCompactionCandidatePort {
    ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source);
}
