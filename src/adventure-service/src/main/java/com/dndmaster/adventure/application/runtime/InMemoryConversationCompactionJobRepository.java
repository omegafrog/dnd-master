package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InMemoryConversationCompactionJobRepository implements ConversationCompactionJobRepository {
    final List<ConversationCompactionJob> jobs = new ArrayList<>();
    final List<ConversationSummary> summaries = new ArrayList<>();
    final List<LongTermAdventureFact> longTermFacts = new ArrayList<>();
    final List<LongTermAdventureFact> longTermFactHistory = new ArrayList<>();
    @Override public synchronized ConversationCompactionJob register(ConversationCompactionJob job) {
        return jobs.stream().filter(existing -> existing.idempotencyKey().equals(job.idempotencyKey())).findFirst().orElseGet(() -> { jobs.add(job); return job; });
    }
    @Override public synchronized Optional<ConversationCompactionJob> lease(AdventureId id, Instant now, Instant until) {
        boolean held = jobs.stream().anyMatch(job -> job.adventureId().equals(id) && job.status() == ConversationCompactionJob.Status.LEASED && job.leaseUntil().isAfter(now));
        if (held) return Optional.empty();
        for (int i = 0; i < jobs.size(); i++) { ConversationCompactionJob job = jobs.get(i); if (job.adventureId().equals(id) && (((job.status() == ConversationCompactionJob.Status.READY || job.status() == ConversationCompactionJob.Status.RETRY_WAIT) && !job.availableAt().isAfter(now)) || (job.status() == ConversationCompactionJob.Status.LEASED && !job.leaseUntil().isAfter(now)))) { job = job.lease(until); jobs.set(i, job); return Optional.of(job); } }
        return Optional.empty();
    }
    @Override public synchronized boolean save(ConversationCompactionJob leasedJob, ConversationCompactionJob updatedJob) {
        for (int i=0;i<jobs.size();i++) if (ownsLease(jobs.get(i), leasedJob)) { jobs.set(i, updatedJob); return true; }
        return false;
    }
    @Override public synchronized boolean manualReview(ConversationCompactionJob leasedJob, String reason) {
        return save(leasedJob, leasedJob.manualReview());
    }
    @Override public synchronized long coveredThrough(AdventureId adventureId) { return jobs.stream().filter(job -> job.adventureId().equals(adventureId)).mapToLong(ConversationCompactionJob::sourceEnd).max().orElse(-1); }
    @Override public synchronized boolean publish(ConversationCompactionJob job, ConversationSummary summary, List<LongTermAdventureFact> facts, long actualVersion) { if (!jobs.stream().anyMatch(current -> ownsLease(current, job)) || actualVersion < job.expectedAdventureVersion() || summaries.stream().anyMatch(value -> value.adventureId().equals(summary.adventureId()) && value.sourceStart() == summary.sourceStart() && value.sourceEnd() == summary.sourceEnd())) return false; summaries.add(summary); longTermFacts.addAll(facts); save(job, job.done()); return true; }
    @Override public synchronized boolean publish(ConversationCompactionJob job, ConversationSummary summary, List<LongTermAdventureFact> facts,
            List<RuntimeAddedFact> confirmedRuntimeFacts, long actualVersion) {
        if (!jobs.stream().anyMatch(current -> ownsLease(current, job)) || actualVersion < job.expectedAdventureVersion()
                || summaries.stream().anyMatch(value -> value.adventureId().equals(summary.adventureId()) && value.sourceStart() == summary.sourceStart() && value.sourceEnd() == summary.sourceEnd())) return false;
        summaries.add(summary);
        longTermFacts.removeIf(record -> {
            boolean stale = record.adventureId().equals(summary.adventureId()) && confirmedRuntimeFacts.stream()
                    .noneMatch(source -> source.factId().equals(record.factId()) && source.establishedTurnId().equals(record.establishedTurnId()));
            if (stale) longTermFactHistory.add(record);
            return stale;
        });
        for (LongTermAdventureFact fact : facts) {
            int existing = -1;
            for (int index = 0; index < longTermFacts.size(); index++) if (longTermFacts.get(index).adventureId().equals(fact.adventureId()) && longTermFacts.get(index).factId().equals(fact.factId())) { existing = index; break; }
            if (existing < 0) longTermFacts.add(fact);
            else {
                LongTermAdventureFact prior = longTermFacts.get(existing);
                longTermFactHistory.add(prior);
                longTermFacts.set(existing, new LongTermAdventureFact(fact.adventureId(), fact.factId(), fact.establishedTurnId(),
                        fact.sourceAdventureVersion(), fact.kind(), fact.relevance(), fact.playerVisible(), prior.version() + 1));
            }
        }
        save(job, job.done()); return true;
    }
    @Override public synchronized List<ConversationSummary> summaries(AdventureId adventureId) { return summaries.stream().filter(value -> value.adventureId().equals(adventureId)).toList(); }
    @Override public synchronized List<LongTermAdventureFact> longTermFacts(AdventureId adventureId) { return longTermFacts.stream().filter(value -> value.adventureId().equals(adventureId)).toList(); }
    @Override public synchronized List<LongTermAdventureFact> longTermFactHistory(AdventureId adventureId) { return longTermFactHistory.stream().filter(value -> value.adventureId().equals(adventureId)).toList(); }
    @Override public synchronized List<ConversationCompactionJob> ready(Instant now) { return jobs.stream().filter(job -> ((job.status() == ConversationCompactionJob.Status.READY || job.status() == ConversationCompactionJob.Status.RETRY_WAIT) && !job.availableAt().isAfter(now)) || (job.status() == ConversationCompactionJob.Status.LEASED && !job.leaseUntil().isAfter(now))).toList(); }
    private static boolean ownsLease(ConversationCompactionJob current, ConversationCompactionJob claimant) { return current.id().equals(claimant.id()) && current.status() == ConversationCompactionJob.Status.LEASED && current.leaseToken() != null && current.leaseToken().equals(claimant.leaseToken()); }
}
