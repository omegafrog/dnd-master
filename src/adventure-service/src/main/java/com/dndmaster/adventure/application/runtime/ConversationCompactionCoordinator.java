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
            if (!validCandidate(job, source, candidate)) {
                candidate = candidatePort.create(job, source);
                if (!validCandidate(job, source, candidate)) throw new TransientConversationCompactionException("CANDIDATE_PROVENANCE_MISMATCH");
            }
            String renderedSummary = candidate.excerpts().stream().map(ConversationCompactionCandidate.SourceExcerpt::text)
                    .collect(java.util.stream.Collectors.joining(" "));
            long summaryVersion = repository.summaries(adventureId).size() + 1;
            boolean published = repository.publish(job, new ConversationSummary(adventureId, summaryVersion, job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), renderedSummary), actualAdventureVersion);
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
    private static boolean validCandidate(ConversationCompactionJob job, List<ConversationEntry> source, ConversationCompactionCandidate candidate) {
        return candidate.sourceStart() == job.sourceStart() && candidate.sourceEnd() == job.sourceEnd()
                && candidate.expectedAdventureVersion() == job.expectedAdventureVersion() && validExcerpts(candidate.excerpts(), source);
    }
    private static boolean validExcerpts(List<ConversationCompactionCandidate.SourceExcerpt> excerpts, List<ConversationEntry> source) {
        if (excerpts == null || excerpts.isEmpty()) return false;
        java.util.Map<Long, ConversationEntry> entries = source.stream().collect(java.util.stream.Collectors.toMap(ConversationEntry::sequence, entry -> entry));
        long previousSequence = -1;
        java.util.Set<Long> covered = new java.util.HashSet<>();
        long excerptLength = 0;
        long excerptCount = 0;
        for (ConversationCompactionCandidate.SourceExcerpt excerpt : excerpts) {
            if (excerpt == null || excerpt.sequence() <= previousSequence) return false;
            ConversationEntry entry = entries.get(excerpt.sequence());
            if (entry == null || excerpt.text() == null || excerpt.text().isBlank() || !entry.content().contains(excerpt.text())) return false;
            previousSequence = excerpt.sequence();
            covered.add(excerpt.sequence());
            excerptLength += excerpt.text().length();
            excerptCount++;
        }
        long inputLength = source.stream().mapToLong(entry -> entry.content().length()).sum();
        return covered.equals(entries.keySet()) && (excerptLength + Math.max(0, excerptCount - 1)) * 5 <= inputLength * 4;
    }
    private static int nthLastPlayerIndex(List<ConversationEntry> conversation, int nth) { int found=0; for(int i=conversation.size()-1;i>=0;i--) if("PLAYER".equals(conversation.get(i).speaker()) && ++found==nth) return i; return -1; }
}
