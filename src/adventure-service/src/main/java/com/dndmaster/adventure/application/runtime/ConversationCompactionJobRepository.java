package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationCompactionJobRepository {
    ConversationCompactionJob register(ConversationCompactionJob job);
    Optional<ConversationCompactionJob> lease(AdventureId adventureId, Instant now, Instant until);
    boolean save(ConversationCompactionJob leasedJob, ConversationCompactionJob updatedJob);
    boolean manualReview(ConversationCompactionJob leasedJob, String reason);
    boolean publish(ConversationCompactionJob job, ConversationSummary summary, long actualAdventureVersion);
    List<ConversationSummary> summaries(AdventureId adventureId);
    default long coveredThrough(AdventureId adventureId) { return summaries(adventureId).stream().mapToLong(ConversationSummary::sourceEnd).max().orElse(-1); }
    /** Ready requests are exposed to the low-priority worker; lease acquisition remains authoritative. */
    default List<ConversationCompactionJob> ready(Instant now) { return List.of(); }
}
