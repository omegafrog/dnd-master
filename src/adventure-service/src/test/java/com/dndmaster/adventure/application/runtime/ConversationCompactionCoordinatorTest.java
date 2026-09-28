package com.dndmaster.adventure.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import org.junit.jupiter.api.Test;

class ConversationCompactionCoordinatorTest {
    @Test
    void publishes_only_long_term_records_that_reference_a_confirmed_runtime_fact() {
        var repository = new InMemoryConversationCompactionJobRepository();
        AdventureId adventureId = AdventureId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        UUID factId = UUID.randomUUID(); UUID turnId = UUID.randomUUID();
        List<ConversationEntry> source = List.of(entry(0, "PLAYER", "경비에게 협력하겠다고 약속한다. ".repeat(20)),
                entry(1, "AI_GAME_MASTER", "경비는 성문을 열어 주기로 한다. ".repeat(20)));
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));
        var coordinator = new ConversationCompactionCoordinator(repository, (job, entries) ->
                new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(),
                        List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "PLAYER", "약속"),
                                new ConversationCompactionCandidate.SourceExcerpt(1, "AI_GAME_MASTER", "열어")),
                        List.of(new LongTermFactCandidate(factId, turnId, "RELATIONSHIP", "경비 성문" , true))));

        assertTrue(coordinator.runOnce(adventureId, new UUID(0L, 0L), 7, source,
                List.of(new RuntimeAddedFact(factId, "경비는 성문을 열어 주기로 했다", turnId, "경비")), now));
        assertEquals(1, repository.longTermFacts(adventureId).size());

        repository = new InMemoryConversationCompactionJobRepository();
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));
        var rejected = new ConversationCompactionCoordinator(repository, (job, entries) ->
                new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(),
                        List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "PLAYER", "약속"),
                                new ConversationCompactionCandidate.SourceExcerpt(1, "AI_GAME_MASTER", "열어")),
                        List.of(new LongTermFactCandidate(UUID.randomUUID(), turnId, "RELATIONSHIP", "경비 성문", true))));
        assertFalse(rejected.runOnce(adventureId, new UUID(0L, 0L), 7, source,
                List.of(new RuntimeAddedFact(factId, "경비는 성문을 열어 주기로 했다", turnId, "경비")), now));
        assertTrue(repository.longTermFacts(adventureId).isEmpty());
    }
    @Test
    void registers_once_for_turns_older_than_the_latest_two_completed_gm_turns_and_keeps_originals() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository, new ConversationCompactionCandidatePort() {
            @Override public ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source) {
                return validCandidate(job, source);
            }
        });
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(
                entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"), entry(2, "AI_GAME_MASTER", "첫 판정"),
                entry(3, "PLAYER", "둘째 행동"), entry(4, "AI_GAME_MASTER", "둘째 응답"), entry(5, "AI_GAME_MASTER", "둘째 판정"),
                entry(6, "PLAYER", "셋째 행동"), entry(7, "AI_GAME_MASTER", "셋째 응답"), entry(8, "AI_GAME_MASTER", "셋째 판정"));

        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, Instant.parse("2026-01-01T00:00:00Z"));
        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(1, repository.jobs.size());
        ConversationCompactionJob job = repository.jobs.getFirst();
        assertEquals(0, job.sourceStart());
        assertEquals(2, job.sourceEnd());
        assertEquals(List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"), entry(2, "AI_GAME_MASTER", "첫 판정")),
                coordinator.source(job, conversation));
        assertEquals(conversation, List.copyOf(conversation));
    }

    @Test
    void treats_a_contiguous_gm_only_response_as_one_completed_turn() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository, ConversationCompactionCoordinatorTest::validCandidate);
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(
                entry(0, "AI_GAME_MASTER", "도입 장면을 설명한다"),
                entry(1, "AI_GAME_MASTER", "상황이 변했다"),
                entry(2, "AI_GAME_MASTER", "선택지를 제시한다"));

        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(List.of(2L), ConversationCompactionCoordinator.completedTurnEnds(conversation));
        assertTrue(repository.jobs.isEmpty());
    }

    @Test
    void treats_confirmed_combat_result_and_its_narration_as_one_completed_turn() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository, ConversationCompactionCoordinatorTest::validCandidate);
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(
                entry(0, "AI_GAME_MASTER", "확정 전투 결과: 오크가 3 피해를 받았다"),
                entry(1, "AI_GAME_MASTER", "오크가 비틀거리며 뒤로 물러난다"),
                entry(2, "PLAYER", "주변을 살핀다"), entry(3, "AI_GAME_MASTER", "전투 뒤 주변이 조용해진다"),
                entry(4, "PLAYER", "앞으로 간다"), entry(5, "AI_GAME_MASTER", "다음 행동을 기다린다"));

        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(List.of(1L, 3L, 5L), ConversationCompactionCoordinator.completedTurnEnds(conversation));
        assertEquals(1, repository.jobs.size());
        assertEquals(0, repository.jobs.getFirst().sourceStart());
        assertEquals(1, repository.jobs.getFirst().sourceEnd());
    }

    @Test
    void makes_one_immediate_retry_then_schedules_a_durable_retry() {
        var repository = new InMemoryConversationCompactionJobRepository();
        AtomicInteger calls = new AtomicInteger();
        var candidatePort = new ConversationCompactionCandidatePort() {
            @Override public ConversationCompactionCandidate create(ConversationCompactionJob job, List<ConversationEntry> source) {
                if (calls.getAndIncrement() < 2) throw new TransientConversationCompactionException("provider unavailable");
                return validCandidate(job, source);
            }
        };
        var coordinator = new ConversationCompactionCoordinator(repository, candidatePort);
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(
                entry(0, "PLAYER", "첫 번째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(1, "AI_GAME_MASTER", "첫 번째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"),
                entry(2, "PLAYER", "둘째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(3, "AI_GAME_MASTER", "둘째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"),
                entry(4, "PLAYER", "셋째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(5, "AI_GAME_MASTER", "셋째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, now);

        assertFalse(coordinator.runOnce(adventureId, 7, conversation, now));
        assertEquals(ConversationCompactionJob.Status.RETRY_WAIT, repository.jobs.getFirst().status());
        assertEquals(2, calls.get());
        assertEquals(0, repository.summaries.size());
        assertEquals(conversation, List.copyOf(conversation));
    }

    @Test
    void moves_to_manual_review_after_the_bounded_durable_attempts_are_exhausted() {
        var repository = new InMemoryConversationCompactionJobRepository();
        AtomicInteger calls = new AtomicInteger();
        var coordinator = new ConversationCompactionCoordinator(repository,
                (job, source) -> { calls.incrementAndGet(); throw new TransientConversationCompactionException("provider unavailable"); });
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"),
                entry(2, "PLAYER", "둘째 행동"), entry(3, "AI_GAME_MASTER", "둘째 응답"), entry(4, "PLAYER", "셋째 행동"), entry(5, "AI_GAME_MASTER", "셋째 응답"));
        Instant now = Instant.parse("2026-01-01T00:00:00Z"); coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, now);
        coordinator.runOnce(adventureId, 7, conversation, now);
        coordinator.runOnce(adventureId, 7, conversation, now.plusSeconds(3));
        coordinator.runOnce(adventureId, 7, conversation, now.plusSeconds(8));
        assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
        assertEquals(6, calls.get());
    }

    @Test
    void transient_retry_then_invalid_candidate_uses_at_most_two_provider_calls_and_manual_review() {
        var repository = new InMemoryConversationCompactionJobRepository();
        AtomicInteger calls = new AtomicInteger();
        var coordinator = new ConversationCompactionCoordinator(repository, (job, source) -> {
            if (calls.getAndIncrement() == 0) throw new TransientConversationCompactionException("provider unavailable");
            return new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(),
                    List.of(new ConversationCompactionCandidate.SourceExcerpt(job.sourceStart(), "근거 없는 결과")));
        });
        AdventureId adventureId = AdventureId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));

        assertFalse(coordinator.runOnce(adventureId, 7,
                List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답")), now));
        assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
        assertEquals(2, calls.get());
        assertTrue(repository.summaries.isEmpty());
    }

    @Test
    void reclaims_an_expired_lease_without_claiming_a_live_worker_lease() {
        var repository = new InMemoryConversationCompactionJobRepository();
        AdventureId adventureId = AdventureId.generate(); Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ConversationCompactionJob job = repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));
        assertTrue(repository.lease(adventureId, now, now.plusSeconds(420)).isPresent());
        // A second 180-second provider attempt can still be running after the first one times out.
        assertFalse(repository.lease(adventureId, now.plusSeconds(360), now.plusSeconds(780)).isPresent());
        ConversationCompactionJob reclaimed = repository.lease(adventureId, now.plusSeconds(421), now.plusSeconds(841)).orElseThrow();
        assertEquals(job.attempts() + 2, reclaimed.attempts());
        assertFalse(java.util.Objects.equals(job.leaseToken(), reclaimed.leaseToken()));
    }

    @Test
    void refuses_incomplete_source_ranges_and_candidates_without_complete_source_references() {
        var repository = new InMemoryConversationCompactionJobRepository();
        AtomicInteger calls = new AtomicInteger();
        var coordinator = new ConversationCompactionCoordinator(repository,
                (job, source) -> { calls.incrementAndGet(); return new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(),
                        job.expectedAdventureVersion(), List.of(new ConversationCompactionCandidate.SourceExcerpt(job.sourceStart(), "무관한 요약"))); });
        AdventureId adventureId = AdventureId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));

        assertFalse(coordinator.runOnce(adventureId, 7, List.of(entry(0, "PLAYER", "첫 행동")), now));
        assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
        assertEquals(0, calls.get());
        assertTrue(repository.summaries.isEmpty());

        repository.jobs.clear();
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));
        assertFalse(coordinator.runOnce(adventureId, 7,
                List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "응답")), now));
        assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
        assertEquals(1, calls.get());
        assertTrue(repository.summaries.isEmpty());
    }

    @Test
    void publishes_only_ordered_exact_source_excerpts_and_renders_them_deterministically() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository, (job, source) ->
                new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(),
                        List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "PLAYER", "행동"),
                                new ConversationCompactionCandidate.SourceExcerpt(1, "AI_GAME_MASTER", "응답"))));
        AdventureId adventureId = AdventureId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));
        List<ConversationEntry> source = List.of(
                entry(0, "PLAYER", "첫 행동으로 문을 열고 조심스럽게 안을 살핀다"),
                entry(1, "AI_GAME_MASTER", "응답으로 문이 열리고 어두운 복도에서 차가운 바람이 불어온다"));

        assertTrue(coordinator.runOnce(adventureId, 7, source, now));
        assertEquals("PLAYER: 행동 AI_GAME_MASTER: 응답", repository.summaries.getFirst().text());
    }

    @Test
    void rejects_candidate_when_speaker_labels_make_the_rendered_summary_exceed_eighty_percent() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository, (job, source) ->
                new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(),
                        List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "PLAYER", "abc"),
                                new ConversationCompactionCandidate.SourceExcerpt(1, "AI_GAME_MASTER", "abc"))));
        AdventureId adventureId = AdventureId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));

        assertFalse(coordinator.runOnce(adventureId, 7,
                List.of(entry(0, "PLAYER", "abcde"), entry(1, "AI_GAME_MASTER", "abcde")), now));
        assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
        assertTrue(repository.summaries.isEmpty());
    }

    @Test
    void rejects_unsupported_missing_extra_and_out_of_order_excerpts_without_publishing() {
        List<List<ConversationCompactionCandidate.SourceExcerpt>> invalid = List.of(
                List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "무관한 문장"), new ConversationCompactionCandidate.SourceExcerpt(1, "첫 응")),
                List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "행동")),
                List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "행동"), new ConversationCompactionCandidate.SourceExcerpt(1, "응답"), new ConversationCompactionCandidate.SourceExcerpt(2, "초과")),
                List.of(new ConversationCompactionCandidate.SourceExcerpt(1, "응답"), new ConversationCompactionCandidate.SourceExcerpt(0, "행동")),
                List.of(new ConversationCompactionCandidate.SourceExcerpt(0, "첫 행"), new ConversationCompactionCandidate.SourceExcerpt(1, "첫 응")));
        for (List<ConversationCompactionCandidate.SourceExcerpt> excerpts : invalid) {
            var repository = new InMemoryConversationCompactionJobRepository();
            AtomicInteger calls = new AtomicInteger();
            AdventureId adventureId = AdventureId.generate();
            Instant now = Instant.parse("2026-01-01T00:00:00Z");
            repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));
            var coordinator = new ConversationCompactionCoordinator(repository, (job, source) -> {
                calls.incrementAndGet();
                return new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), excerpts);
            });

            assertFalse(coordinator.runOnce(adventureId, 7,
                    List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답")), now));
            assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
            assertEquals(1, calls.get());
            assertTrue(repository.summaries.isEmpty());
        }
    }

    @Test
    void expired_worker_cannot_save_or_publish_after_a_new_lease_is_acquired() {
        var repository = new InMemoryConversationCompactionJobRepository();
        AdventureId adventureId = AdventureId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ConversationCompactionJob original = repository.register(ConversationCompactionJob.ready(adventureId, 0, 1, 7, now));
        ConversationCompactionJob stale = repository.lease(adventureId, now, now.plusSeconds(10)).orElseThrow();
        ConversationCompactionJob current = repository.lease(adventureId, now.plusSeconds(11), now.plusSeconds(21)).orElseThrow();

        assertFalse(repository.save(stale, stale.retryAt(now.plusSeconds(30))));
        assertFalse(repository.manualReview(stale, "stale worker"));
        assertFalse(repository.publish(stale, new ConversationSummary(adventureId, 1, 0, 1, 7, "낡은 결과"), 7));
        assertTrue(repository.publish(current, new ConversationSummary(adventureId, 1, 0, 1, 7, "현재 결과"), 7));
        assertEquals(ConversationCompactionJob.Status.DONE, repository.jobs.getFirst().status());
        assertEquals("현재 결과", repository.summaries.getFirst().text());
        assertEquals(original.id(), current.id());
    }

    @Test
    void source_version_regression_never_leaves_a_candidate_leased() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository,
                ConversationCompactionCoordinatorTest::validCandidate);
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(
                entry(0, "PLAYER", "첫 번째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(1, "AI_GAME_MASTER", "첫 번째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"),
                entry(2, "PLAYER", "둘째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(3, "AI_GAME_MASTER", "둘째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"),
                entry(4, "PLAYER", "셋째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(5, "AI_GAME_MASTER", "셋째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, now);
        assertFalse(coordinator.runOnce(adventureId, 6, conversation, now));
        assertEquals(ConversationCompactionJob.Status.MANUAL_REVIEW, repository.jobs.getFirst().status());
    }

    @Test
    void later_confirmed_turns_do_not_reject_a_summary_for_an_unchanged_source_range() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository,
                ConversationCompactionCoordinatorTest::validCandidate);
        AdventureId adventureId = AdventureId.generate();
        List<ConversationEntry> conversation = List.of(
                entry(0, "PLAYER", "첫 번째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(1, "AI_GAME_MASTER", "첫 번째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"),
                entry(2, "PLAYER", "둘째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(3, "AI_GAME_MASTER", "둘째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"),
                entry(4, "PLAYER", "셋째 행동을 충분히 길게 설명하고 주변의 상황까지 자세히 살핀다"), entry(5, "AI_GAME_MASTER", "셋째 응답도 충분히 길게 설명하며 장면의 변화를 자세히 묘사한다"));
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        coordinator.registerAfterConfirmedTurn(adventureId, 7, conversation, now);
        assertTrue(coordinator.runOnce(adventureId, 8, conversation, now));
        assertEquals(ConversationCompactionJob.Status.DONE, repository.jobs.getFirst().status());
    }

    @Test
    void later_registration_starts_after_already_registered_source_range() {
        var repository = new InMemoryConversationCompactionJobRepository();
        var coordinator = new ConversationCompactionCoordinator(repository, (job, source) -> { throw new AssertionError(); });
        AdventureId adventureId = AdventureId.generate(); Instant now = Instant.parse("2026-01-01T00:00:00Z");
        List<ConversationEntry> first = List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"), entry(2, "PLAYER", "둘째 행동"), entry(3, "AI_GAME_MASTER", "둘째 응답"), entry(4, "PLAYER", "셋째 행동"), entry(5, "AI_GAME_MASTER", "셋째 응답"));
        coordinator.registerAfterConfirmedTurn(adventureId, 7, first, now);
        List<ConversationEntry> second = List.of(entry(0, "PLAYER", "첫 행동"), entry(1, "AI_GAME_MASTER", "첫 응답"), entry(2, "PLAYER", "둘째 행동"), entry(3, "AI_GAME_MASTER", "둘째 응답"), entry(4, "PLAYER", "셋째 행동"), entry(5, "AI_GAME_MASTER", "셋째 응답"), entry(6, "PLAYER", "넷째 행동"), entry(7, "AI_GAME_MASTER", "넷째 응답"));
        coordinator.registerAfterConfirmedTurn(adventureId, 8, second, now);
        assertEquals(2, repository.jobs.size());
        assertEquals(0, repository.jobs.get(0).sourceStart());
        assertEquals(2, repository.jobs.get(1).sourceStart());
    }

    private static ConversationEntry entry(long sequence, String speaker, String content) {
        return new ConversationEntry(sequence, speaker, content);
    }

    private static ConversationCompactionCandidate validCandidate(ConversationCompactionJob job, List<ConversationEntry> source) {
        List<ConversationCompactionCandidate.SourceExcerpt> excerpts = source.stream()
                .map(entry -> new ConversationCompactionCandidate.SourceExcerpt(entry.sequence(), entry.speaker(), entry.content().substring(0, 1)))
                .toList();
        return new ConversationCompactionCandidate(job.sourceStart(), job.sourceEnd(), job.expectedAdventureVersion(), excerpts);
    }
}
