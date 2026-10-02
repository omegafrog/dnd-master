package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import java.time.Instant;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls one durable request at a time; provider execution never runs in the turn request. */
public final class ConversationCompactionWorker {
    private final ConversationCompactionJobRepository jobs;
    private final AdventureRepository adventures;
    private final ConversationCompactionCoordinator coordinator;

    public ConversationCompactionWorker(ConversationCompactionJobRepository jobs, AdventureRepository adventures,
                                        ConversationCompactionCandidatePort candidates) {
        this.jobs = Objects.requireNonNull(jobs); this.adventures = Objects.requireNonNull(adventures);
        this.coordinator = new ConversationCompactionCoordinator(jobs, Objects.requireNonNull(candidates));
    }

    @Scheduled(scheduler = "conversationCompactionScheduler", fixedDelayString = "${adventure.conversation-compaction.poll-delay-ms:1000}")
    public void process() { processOnce(Instant.now()); }

    public boolean processOnce(Instant now) {
        // The repository lease is per adventure. Try only adventures that own ready work through the job list.
        for (ConversationCompactionJob job : jobs.ready(now)) {
            var adventure = adventures.findById(job.adventureId()).orElse(null);
            if (adventure != null) return coordinator.runOnce(job.adventureId(), adventure.ownerPlayerId().value(),
                    adventure.version(), adventure.conversation(), adventure.runtimeAddedFacts(), now);
        }
        return false;
    }
}
