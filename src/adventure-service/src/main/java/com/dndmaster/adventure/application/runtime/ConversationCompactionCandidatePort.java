package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;
import java.util.UUID;

@FunctionalInterface
public interface ConversationCompactionCandidatePort {
    ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source);

    /** Creates a candidate under the adventure owner's provider identity. */
    default ConversationCompactionCandidate create(UUID ownerPlayerId, ConversationCompactionJob job,
            List<ConversationEntry> source) {
        return create(job, source);
    }
}
