package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationCompactionJobRepository {
    ConversationCompactionJob register(ConversationCompactionJob job);
    Optional<ConversationCompactionJob> lease(AdventureId adventureId, Instant now, Instant until);
    boolean save(ConversationCompactionJob leasedJob, ConversationCompactionJob updatedJob);
    boolean manualReview(ConversationCompactionJob leasedJob, String reason);
    boolean publish(ConversationCompactionJob job, ConversationSummary summary, List<LongTermAdventureFact> facts, long actualAdventureVersion);
    /** Publishes lookup records only while their confirmed Runtime sources still exist. */
    default boolean publish(ConversationCompactionJob job, ConversationSummary summary, List<LongTermAdventureFact> facts,
            List<RuntimeAddedFact> confirmedRuntimeFacts, long actualAdventureVersion) {
        return publish(job, summary, facts, actualAdventureVersion);
    }
    default boolean publish(ConversationCompactionJob job, ConversationSummary summary, long actualAdventureVersion) {
        return publish(job, summary, List.of(), actualAdventureVersion);
    }
    List<ConversationSummary> summaries(AdventureId adventureId);
    default List<LongTermAdventureFact> longTermFacts(AdventureId adventureId) { return List.of(); }
    default long coveredThrough(AdventureId adventureId) { return summaries(adventureId).stream().mapToLong(ConversationSummary::sourceEnd).max().orElse(-1); }
    /** Ready requests are exposed to the low-priority worker; lease acquisition remains authoritative. */
    default List<ConversationCompactionJob> ready(Instant now) { return List.of(); }
}
