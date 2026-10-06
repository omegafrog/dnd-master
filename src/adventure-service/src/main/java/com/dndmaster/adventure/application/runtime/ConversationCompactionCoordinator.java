package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Registers after a confirmed turn; provider work is always performed later by runOnce. */
public final class ConversationCompactionCoordinator {
    private static final int MAX_DURABLE_ATTEMPTS = 3;
    // Two internal AI calls may each wait 180 seconds; retain a minute for processing before recovery is allowed.
    private static final Duration LEASE = Duration.ofMinutes(7);
    private final ConversationCompactionJobRepository repository;
    private final ConversationCompactionCandidatePort candidatePort;
    public ConversationCompactionCoordinator(ConversationCompactionJobRepository repository, ConversationCompactionCandidatePort candidatePort) { this.repository = Objects.requireNonNull(repository); this.candidatePort = Objects.requireNonNull(candidatePort); }
    public void registerAfterConfirmedTurn(AdventureId adventureId, long version, List<ConversationEntry> conversation, Instant now) {
        List<Long> completedEnds = completedTurnEnds(conversation);
        if (completedEnds.size() < 3) return;
        long sourceStart = Math.max(conversation.getFirst().sequence(), repository.coveredThrough(adventureId) + 1);
        long sourceEnd = completedEnds.get(completedEnds.size() - 3);
        if (sourceStart > sourceEnd) return;
        repository.register(ConversationCompactionJob.ready(adventureId, sourceStart, sourceEnd, version, now));
    }
    public boolean runOnce(AdventureId adventureId, long actualAdventureVersion, List<ConversationEntry> conversation, Instant now) {
        return runOnce(adventureId, new UUID(0L, 0L), actualAdventureVersion, conversation, now);
    }
    public boolean runOnce(AdventureId adventureId, UUID ownerPlayerId, long actualAdventureVersion,
                           List<ConversationEntry> conversation, Instant now) {
        return runOnce(adventureId, ownerPlayerId, actualAdventureVersion, conversation, List.of(), now);
    }
    public boolean runOnce(AdventureId adventureId, UUID ownerPlayerId, long actualAdventureVersion,
                           List<ConversationEntry> conversation, List<RuntimeAddedFact> runtimeFacts, Instant now) {
        var leased = repository.lease(adventureId, now, now.plus(LEASE)); if (leased.isEmpty()) return false;
        ConversationCompactionJob job = leased.get();
        try {
            List<ConversationEntry> source = source(job, conversation);
            if (!completeRange(job, source)) { repository.manualReview(job, "SOURCE_RANGE_INCOMPLETE"); return false; }
            ConversationCompactionCandidate candidate;
            try {
                candidate = candidatePort.create(ownerPlayerId, job, source, runtimeFacts);
            } catch (TransientConversationCompactionException first) {
                try { candidate = candidatePort.create(ownerPlayerId, job, source, runtimeFacts); }
                catch (TransientConversationCompactionException second) {
                    second.addSuppressed(first);
                    throw second;
                }
            }
            String candidateFailure = candidateFailure(job, source, candidate, runtimeFacts);
            if (candidateFailure != null) {
                repository.manualReview(job, candidateFailure);
                return false;
            }
            String renderedSummary = candidate.summary().trim();
            long summaryVersion = repository.summaries(adventureId).size() + 1;
            List<LongTermAdventureFact> facts = longTermFacts(adventureId, job, actualAdventureVersion, candidate, runtimeFacts);
            boolean published = repository.publish(job, new ConversationSummary(adventureId, summaryVersion, job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), renderedSummary), facts, runtimeFacts, actualAdventureVersion);
            if (!published) repository.manualReview(job, "SOURCE_RANGE_OR_VERSION_REJECTED");
            return published;
        } catch (TransientConversationCompactionException error) {
            if (job.attempts() >= MAX_DURABLE_ATTEMPTS) repository.manualReview(job, "TRANSIENT_RETRY_EXHAUSTED: " + error.getMessage());
            else repository.save(job, job.retryAt(now.plusSeconds(1L << Math.min(job.attempts(), 6))));
            return false;
        } catch (RuntimeException error) { repository.manualReview(job, "PERMANENT_CANDIDATE_FAILURE: " + error.getMessage()); return false; }
    }
    List<ConversationEntry> source(ConversationCompactionJob job, List<ConversationEntry> conversation) { return conversation.stream().filter(entry -> entry.sequence() >= job.sourceStart() && entry.sequence() <= job.sourceEnd()).toList(); }
    private static boolean completeRange(ConversationCompactionJob job, List<ConversationEntry> source) {
        long expectedCount = job.sourceEnd() - job.sourceStart() + 1;
        return expectedCount > 0 && source.size() == expectedCount
                && source.stream().map(ConversationEntry::sequence).distinct().count() == expectedCount
                && source.getFirst().sequence() == job.sourceStart() && source.getLast().sequence() == job.sourceEnd();
    }
    static String candidateFailure(ConversationCompactionJob job, List<ConversationEntry> source,
                                   ConversationCompactionCandidate candidate, List<RuntimeAddedFact> runtimeFacts) {
        if (candidate.sourceStart() != job.sourceStart() || candidate.sourceEnd() != job.sourceEnd()
                || candidate.expectedAdventureVersion() != job.expectedAdventureVersion()) return "CANDIDATE_SOURCE_OR_VERSION_MISMATCH";
        if (candidate.summary() == null || candidate.summary().isBlank()) return "CANDIDATE_SUMMARY_EMPTY";
        long summaryLength = candidate.summary().trim().length();
        long sourceLength = source.stream().mapToLong(entry -> entry.content().length()).sum();
        if (summaryLength * 5 > sourceLength * 4) return "CANDIDATE_SUMMARY_TOO_LONG";
        if (candidate.longTermFacts().stream().anyMatch(fact -> !matchesConfirmedFact(fact, runtimeFacts)))
            return "CANDIDATE_FACT_REFERENCE_MISMATCH";
        return null;
    }
    private static boolean matchesConfirmedFact(LongTermFactCandidate candidate, List<RuntimeAddedFact> runtimeFacts) {
        return confirmedFact(candidate, runtimeFacts) != null;
    }
    private static List<LongTermAdventureFact> longTermFacts(AdventureId adventureId, ConversationCompactionJob job,
            long actualAdventureVersion, ConversationCompactionCandidate candidate, List<RuntimeAddedFact> runtimeFacts) {
        return candidate.longTermFacts().stream().map(proposed -> {
            RuntimeAddedFact confirmed = confirmedFact(proposed, runtimeFacts);
            // The candidate chooses only a declared kind; the persisted fact text and visibility come from confirmed play.
            // Runtime-added facts are created only from confirmed player-visible turn results.
            return new LongTermAdventureFact(adventureId, confirmed.factId(), confirmed.establishedTurnId(),
                    actualAdventureVersion, proposed.kind(), confirmed.content(), true, 1);
        }).toList();
    }
    private static RuntimeAddedFact confirmedFact(LongTermFactCandidate candidate, List<RuntimeAddedFact> runtimeFacts) {
        return runtimeFacts.stream().filter(fact -> fact.factId().equals(candidate.factId())
                && fact.establishedTurnId().equals(candidate.establishedTurnId())).findFirst().orElse(null);
    }
    /** A contiguous AI Game Master response is one completed turn; a player entry starts the next turn. */
    static List<Long> completedTurnEnds(List<ConversationEntry> conversation) {
        List<Long> ends = new java.util.ArrayList<>();
        for (int index = 0; index < conversation.size(); index++) {
            ConversationEntry entry = conversation.get(index);
            if (!"AI_GAME_MASTER".equals(entry.speaker())) continue;
            long end = entry.sequence();
            while (index + 1 < conversation.size() && "AI_GAME_MASTER".equals(conversation.get(index + 1).speaker())) {
                end = conversation.get(++index).sequence();
            }
            ends.add(end);
        }
        return ends;
    }
}
