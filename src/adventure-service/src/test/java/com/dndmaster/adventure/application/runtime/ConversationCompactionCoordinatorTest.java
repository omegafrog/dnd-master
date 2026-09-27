package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationCompactionCoordinatorTest {
    @Test
    void registers_once_for_turns_older_than_the_latest_two_completed_gm_turns_and_keeps_originals() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository, new ConversationCompactionCandidatePort() {
            @Override public ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source) {
                return new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), "요약");
            }
        });
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(
                entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"),
                entry(2, "PLAYER", "둘째 행동"), entry(3, "AI_GAME_MASTER", "둘째 응답"),
                entry(4, "PLAYER", "셋째 행동"), entry(5, "AI_GAME_MASTER", "셋째 응답"));

        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, Instant.parse("2026-01-01T00:00:00Z"));
        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(1, repository.jobs.size());
        ConversationCompactionJob job = repository.jobs.getFirst();
        assertEquals(0, job.sourceStart());
        assertEquals(1, job.sourceEnd());
        assertEquals(List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답")),
                coordinator.source(job, conversation));
        assertEquals(conversation, List.copyOf(conversation));
    }

    @Test
    void leases_once_retries_transient_failure_and_publishes_only_matching_candidate_version() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var candidatePort = new ConversationCompactionCandidatePort() {
            int calls;
            @Override public ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source) {
                if (calls++ == 0) throw new TransientConversationCompactionException("provider unavailable");
                return new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), "첫 장면 요약");
            }
        };
        var coordinator = new ConversationCompactionCoordinator(repository, candidatePort);
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"),
                entry(2, "PLAYER", "둘째 행동"), entry(3, "AI_GAME_MASTER", "둘째 응답"), entry(4, "PLAYER", "셋째 행동"), entry(5, "AI_GAME_MASTER", "셋째 응답"));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, now);

        assertFalse(coordinator.runOnce(adventureId, 7, conversation, now));
        assertEquals(ConversationCompactionJob.Status.RETRY_WAIT, repository.jobs.getFirst().status());
        assertTrue(coordinator.runOnce(adventureId, 7, conversation, now.plusSeconds(2)));
        assertEquals(ConversationCompactionJob.Status.DONE, repository.jobs.getFirst().status());
        assertEquals(1, repository.summaries.size());
        assertEquals("첫 장면 요약", repository.summaries.getFirst().text());
    }

    @Test
    void version_conflict_never_leaves_a_late_candidate_leased() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository,
                (job, source) -> new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), "요약"));
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"),
                entry(2, "PLAYER", "둘째 행동"), entry(3, "AI_GAME_MASTER", "둘째 응답"), entry(4, "PLAYER", "셋째 행동"), entry(5, "AI_GAME_MASTER", "셋째 응답"));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, now);
        assertFalse(coordinator.runOnce(adventureId, 8, conversation, now));
        assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
    }

    private static ConversationEntry entry(long sequence, String speaker, String content) {
        return new ConversationEntry(sequence, speaker, content);
    }
}
