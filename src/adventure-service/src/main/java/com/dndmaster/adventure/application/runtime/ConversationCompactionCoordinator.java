package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Registers after a confirmed turn; provider work is always performed later by runOnce. */
public final class ConversationCompactionCoordinator {
    private static final int MAX_DURABLE_ATTEMPTS = 3;
    // Two internal AI calls may each wait 180 seconds; retain a minute for processing before recovery is allowed.
    private static final Duration LEASE = Duration.ofMinutes(7);
    private final ConversationCompactionJobRepository repository;
    private final ConversationCompactionCandidatePort candidatePort;
    public ConversationCompactionCoordinator(ConversationCompactionJobRepository repository, ConversationCompactionCandidatePort candidatePort) { this.repository = Objects.requireNonNull(repository); this.candidatePort = Objects.requireNonNull(candidatePort); }
    public void registerAfterConfirmedTurn(AdventureId adventureId, long version, List<ConversationEntry> conversation, Instant now) {
        int secondLastPlayer = nthLastPlayerIndex(conversation, 2); if (secondLastPlayer <= 0) return;
        long sourceStart = Math.max(conversation.getFirst().sequence(), repository.coveredThrough(adventureId) + 1);
        long sourceEnd = conversation.get(secondLastPlayer - 1).sequence();
        if (sourceStart > sourceEnd) return;
        repository.register(ConversationCompactionJob.ready(adventureId, sourceStart, sourceEnd, version, now));
    }
    public boolean runOnce(AdventureId adventureId, long actualAdventureVersion, List<ConversationEntry> conversation, Instant now) {
        var leased = repository.lease(adventureId, now, now.plus(LEASE)); if (leased.isEmpty()) return false;
        ConversationCompactionJob job = leased.get();
        try {
            List<ConversationEntry> source = source(job, conversation);
            if (!completeRange(job, source)) { repository.manualReview(job, "SOURCE_RANGE_INCOMPLETE"); return false; }
            ConversationCompactionCandidate candidate = candidate(job, source);
            if (candidate.sourceStart() != job.sourceStart() || candidate.sourceEnd() != job.sourceEnd() || candidate.expectedAdventureVersion() != job.expectedAdventureVersion()
                    || !candidate.referencedSequences().equals(source.stream().map(ConversationEntry::sequence).toList())) { repository.manualReview(job, "CANDIDATE_PROVENANCE_MISMATCH"); return false; }
            long summaryVersion = repository.summaries(adventureId).size() + 1;
            boolean published = repository.publish(job, new ConversationSummary(adventureId, summaryVersion, job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), candidate.text()), actualAdventureVersion);
            if (!published) repository.manualReview(job, "SOURCE_RANGE_OR_VERSION_REJECTED");
            return published;
        } catch (TransientConversationCompactionException error) {
            if (job.attempts() >= MAX_DURABLE_ATTEMPTS) repository.manualReview(job, "TRANSIENT_RETRY_EXHAUSTED: " + error.getMessage());
            else repository.save(job, job.retryAt(now.plusSeconds(1L << Math.min(job.attempts(), 6))));
            return false;
        } catch (RuntimeException error) { repository.manualReview(job, "PERMANENT_CANDIDATE_FAILURE: " + error.getMessage()); return false; }
    }
    List<ConversationEntry> source(ConversationCompactionJob job, List<ConversationEntry> conversation) { return conversation.stream().filter(entry -> entry.sequence() >= job.sourceStart() && entry.sequence() <= job.sourceEnd()).toList(); }
    private ConversationCompactionCandidate candidate(ConversationCompactionJob job, List<ConversationEntry> source) {
        try { return candidatePort.create(job, source); }
        catch (TransientConversationCompactionException first) { return candidatePort.create(job, source); }
    }
    private static boolean completeRange(ConversationCompactionJob job, List<ConversationEntry> source) {
        long expectedCount = job.sourceEnd() - job.sourceStart() + 1;
        return expectedCount > 0 && source.size() == expectedCount
                && source.stream().map(ConversationEntry::sequence).distinct().count() == expectedCount
                && source.getFirst().sequence() == job.sourceStart() && source.getLast().sequence() == job.sourceEnd();
    }
    private static int nthLastPlayerIndex(List<ConversationEntry> conversation, int nth) { int found=0; for(int i=conversation.size()-1;i>=0;i--) if("PLAYER".equals(conversation.get(i).speaker()) && ++found==nth) return i; return -1; }
}
