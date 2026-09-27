package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InMemoryConversationCompactionJobRepository implements ConversationCompactionJobRepository {
    final List<ConversationCompactionJob> jobs = new ArrayList<>();
    final List<ConversationSummary> summaries = new ArrayList<>();
    @Override public synchronized ConversationCompactionJob register(ConversationCompactionJob job) {
        return jobs.stream().filter(existing -> existing.idempotencyKey().equals(job.idempotencyKey())).findFirst().orElseGet(() -> { jobs.add(job); return job; });
    }
    @Override public synchronized Optional<ConversationCompactionJob> lease(AdventureId id, Instant now, Instant until) {
        boolean held = jobs.stream().anyMatch(job -> job.adventureId().equals(id) && job.status() == ConversationCompactionJob.Status.LEASED && job.leaseUntil().isAfter(now));
        if (held) return Optional.empty();
        for (int i = 0; i < jobs.size(); i++) { ConversationCompactionJob job = jobs.get(i); if (job.adventureId().equals(id) && (job.status() == ConversationCompactionJob.Status.READY || job.status() == ConversationCompactionJob.Status.RETRY_WAIT) && !job.availableAt().isAfter(now)) { job = job.lease(until); jobs.set(i, job); return Optional.of(job); } }
        return Optional.empty();
    }
    @Override public synchronized void save(ConversationCompactionJob job) { for (int i=0;i<jobs.size();i++) if (jobs.get(i).id().equals(job.id())) { jobs.set(i, job); return; } throw new IllegalArgumentException("unknown compaction job"); }
    @Override public synchronized long coveredThrough(AdventureId adventureId) { return jobs.stream().filter(job -> job.adventureId().equals(adventureId)).mapToLong(ConversationCompactionJob::sourceEnd).max().orElse(-1); }
    @Override public synchronized boolean publish(ConversationCompactionJob job, ConversationSummary summary, long actualVersion) { if (actualVersion < job.expectedAdventureVersion() || summaries.stream().anyMatch(value -> value.adventureId().equals(summary.adventureId()) && value.sourceStart() == summary.sourceStart() && value.sourceEnd() == summary.sourceEnd())) return false; summaries.add(summary); save(job.done()); return true; }
    @Override public synchronized List<ConversationSummary> summaries(AdventureId adventureId) { return summaries.stream().filter(value -> value.adventureId().equals(adventureId)).toList(); }
    @Override public synchronized List<ConversationCompactionJob> ready(Instant now) { return jobs.stream().filter(job -> (job.status() == ConversationCompactionJob.Status.READY || job.status() == ConversationCompactionJob.Status.RETRY_WAIT) && !job.availableAt().isAfter(now)).toList(); }
}
