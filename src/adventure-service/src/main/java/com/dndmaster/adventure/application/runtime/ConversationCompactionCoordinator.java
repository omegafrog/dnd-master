package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Registers after a confirmed turn; provider work is always performed later by runOnce. */
public final class ConversationCompactionCoordinator {
    private static final int MAX_ATTEMPTS = 3;
    private final ConversationCompactionJobRepository repository;
    private final ConversationCompactionCandidatePort candidatePort;
    public ConversationCompactionCoordinator(ConversationCompactionJobRepository repository, ConversationCompactionCandidatePort candidatePort) { this.repository = Objects.requireNonNull(repository); this.candidatePort = Objects.requireNonNull(candidatePort); }
    public void registerAfterConfirmedTurn(AdventureId adventureId, long version, List<ConversationEntry> conversation, Instant now) {
        int secondLastPlayer = nthLastPlayerIndex(conversation, 2); if (secondLastPlayer <= 0) return;
        repository.register(ConversationCompactionJob.ready(adventureId, conversation.getFirst().sequence(), conversation.get(secondLastPlayer - 1).sequence(), version, now));
    }
    public boolean runOnce(AdventureId adventureId, long actualAdventureVersion, List<ConversationEntry> conversation, Instant now) {
        var leased = repository.lease(adventureId, now, now.plus(Duration.ofMinutes(1))); if (leased.isEmpty()) return false;
        ConversationCompactionJob job = leased.get();
        try { ConversationCompactionCandidate candidate = candidate(job, conversation);
            if (candidate.sourceStart() != job.sourceStart() || candidate.sourceEnd() != job.sourceEnd() || candidate.expectedAdventureVersion() != job.expectedAdventureVersion()) { repository.save(job.manualReview()); return false; }
            long summaryVersion = repository.summaries(adventureId).size() + 1;
            boolean published = repository.publish(job, new ConversationSummary(adventureId, summaryVersion, job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), candidate.text()), actualAdventureVersion);
            if (!published) repository.save(job.manualReview());
            return published;
        } catch (TransientConversationCompactionException error) { repository.save(job.attempts() >= MAX_ATTEMPTS ? job.manualReview() : job.retryAt(now.plusSeconds(1L << Math.min(job.attempts(), 6)))); return false;
        } catch (RuntimeException error) { repository.save(job.manualReview()); return false; }
    }
    List<ConversationEntry> source(ConversationCompactionJob job, List<ConversationEntry> conversation) { return conversation.stream().filter(entry -> entry.sequence() >= job.sourceStart() && entry.sequence() <= job.sourceEnd()).toList(); }
    private ConversationCompactionCandidate candidate(ConversationCompactionJob job, List<ConversationEntry> conversation) {
        try { return candidatePort.create(job, source(job, conversation)); }
        catch (TransientConversationCompactionException first) { return candidatePort.create(job, source(job, conversation)); }
    }
    private static int nthLastPlayerIndex(List<ConversationEntry> conversation, int nth) { int found=0; for(int i=conversation.size()-1;i>=0;i--) if("PLAYER".equals(conversation.get(i).speaker()) && ++found==nth) return i; return -1; }
}
