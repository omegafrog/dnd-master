package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.util.List;
import java.util.UUID;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;

@FunctionalInterface
public interface ConversationCompactionCandidatePort {
    ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source);

    /** Creates a candidate under the adventure owner's provider identity. */
    default ConversationCompactionCandidate create(UUID ownerPlayerId, ConversationCompactionJob job,
            List<ConversationEntry> source) {
        return create(job, source);
    }
    default ConversationCompactionCandidate create(UUID ownerPlayerId, ConversationCompactionJob job,
            List<ConversationEntry> source, List<RuntimeAddedFact> facts) {
        return create(ownerPlayerId, job, source);
    }
}
