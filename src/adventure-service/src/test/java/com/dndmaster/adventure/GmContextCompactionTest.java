package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.domain.runtime.checkpoint.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GmContextCompactionTest {
    @Test
    void schedules_once_only_after_seventy_percent_and_recalculates_limit_per_provider() {
        CompactionPolicy policy = new CompactionPolicy(0.70);

        assertFalse(policy.shouldSchedule(new ContextUsage(699, 1_000), false));
        assertTrue(policy.shouldSchedule(new ContextUsage(700, 1_000), false));
        assertFalse(policy.shouldSchedule(new ContextUsage(900, 1_000), true));
        assertTrue(policy.shouldSchedule(new ContextUsage(800, 1_000), false));
        assertEquals(1_000, new ProviderTokenEstimator(Map.of("local", 1_000, "remote", 2_000)).limit("local"));
        assertEquals(2_000, new ProviderTokenEstimator(Map.of("local", 1_000, "remote", 2_000)).limit("remote"));
    }

    @Test
    void scheduler_isolated_per_session_and_retries_after_failure() {
        GmContextCompactionScheduler scheduler = new GmContextCompactionScheduler(new CompactionPolicy(0.70));
        UUID first = UUID.randomUUID();
        assertFalse(scheduler.scheduleAfterCommit(first, new ContextUsage(800, 1_000), CompactionBarrier.clear(), () -> false));
        assertFalse(scheduler.scheduled(first));
        assertTrue(scheduler.scheduleAfterCommit(first, new ContextUsage(800, 1_000), CompactionBarrier.clear(), () -> true));
        assertTrue(scheduler.scheduleAfterCommit(UUID.randomUUID(), new ContextUsage(800, 1_000), CompactionBarrier.clear(), () -> true));
    }

    @Test
    void barrier_blocks_active_work_or_stale_state() {
        CompactionPolicy policy = new CompactionPolicy(0.70);
        assertFalse(policy.canCompact(new CompactionBarrier(true, false, false, false, false)));
        assertFalse(policy.canCompact(new CompactionBarrier(false, true, false, false, false)));
        assertFalse(policy.canCompact(new CompactionBarrier(false, false, true, false, false)));
        assertFalse(policy.canCompact(new CompactionBarrier(false, false, false, true, false)));
        assertFalse(policy.canCompact(new CompactionBarrier(false, false, false, false, true)));
        assertTrue(policy.canCompact(CompactionBarrier.clear()));
    }

    @Test
    void checkpoint_preserves_exact_tail_and_rejects_malformed_summary() {
        UUID session = UUID.randomUUID();
        ExactTail tail = new ExactTail("player\ntext", "scene", "gm response", "turn 4", "round 2", "dock", "map-state", "fog", "choose door");
        UUID planRevision = UUID.randomUUID();
        GmContextCheckpoint checkpoint = GmContextCheckpoint.create(session, UUID.randomUUID(), 3,
                new ContextSummaryCandidate("summary", List.of("threat"), planRevision, 4), tail,
                new SnapshotReferences(planRevision, 7, 9, 11, 13, 15));

        assertEquals("player\ntext", checkpoint.exactTail().playerInput());
        assertEquals("gm response", checkpoint.exactTail().lastGmResponse());
        assertThrows(IllegalArgumentException.class, () -> new ContextSummaryCandidate("", List.of(), UUID.randomUUID(), 1));
    }

    @Test
    void checkpoint_repository_is_append_only_and_idempotent() {
        InMemoryGmContextCheckpointRepository repository = new InMemoryGmContextCheckpointRepository();
        UUID session = UUID.randomUUID();
        GmContextCheckpoint first = checkpoint(session, 1);

        repository.append(first);
        repository.append(first);

        assertEquals(first, repository.current(session).orElseThrow());
        assertEquals(List.of(first), repository.history(session));
        assertThrows(IllegalStateException.class, () -> repository.append(checkpoint(session, 3)));
    }

    @Test
    void resume_assembler_uses_checkpoint_and_fresh_authoritative_refs() {
        GmContextCheckpoint checkpoint = checkpoint(UUID.randomUUID(), 1);
        ResumedGmContext context = new ResumedGmContextAssembler().assemble(checkpoint,
                new AuthoritativeRuntimeSnapshots("character-v2", "map-v4", "facts-v8", "clock-v3"));

        assertEquals(checkpoint.summary(), context.summary());
        assertEquals(checkpoint.exactTail(), context.exactTail());
        assertEquals("character-v2", context.characterSnapshot());
        assertEquals("map-v4", context.mapSnapshot());
        assertEquals("facts-v8", context.factSnapshot());
        assertEquals("clock-v3", context.clockSnapshot());
    }

    @Test
    void resume_rejects_stale_authoritative_snapshot_versions_but_accepts_newer_versions() {
        GmContextCheckpoint checkpoint = checkpoint(UUID.randomUUID(), 1);
        assertThrows(IllegalStateException.class, () -> new ResumedGmContextAssembler().assemble(checkpoint,
                new AuthoritativeRuntimeSnapshots("character", "map", "facts", "clock", 0, 1, 1, 1)));

        ResumedGmContext resumed = new ResumedGmContextAssembler().assemble(checkpoint,
                new AuthoritativeRuntimeSnapshots("character-v2", "map-v2", "facts-v2", "clock-v2", 2, 2, 2, 2));
        assertEquals("character-v2", resumed.characterSnapshot());
        assertEquals("map-v2", resumed.mapSnapshot());
    }

    @Test
    void provider_contract_rejects_candidate_for_wrong_plan() {
        UUID plan = UUID.randomUUID();
        ContextCompactionPort port = new ValidatingContextCompactionPort(request ->
                new com.dndmaster.adventure.domain.runtime.checkpoint.ContextSummaryCandidate("summary", List.of(), UUID.randomUUID(), 1));
        ContextCompactionRequest request = new ContextCompactionRequest(UUID.randomUUID(), UUID.randomUUID(), "context",
                new ExactTail("input", "scene", "response", "turn", "round", "location", "map", "fog", "choice"),
                new SnapshotReferences(plan, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> port.summarize(request));
    }

    @Test
    void failed_compaction_is_reported_as_failure_even_when_old_checkpoint_exists() {
        UUID session = UUID.randomUUID();
        InMemoryGmContextCheckpointRepository repository = new InMemoryGmContextCheckpointRepository();
        repository.append(checkpoint(session, 1));
        GmContextCheckpointApplicationService service = new GmContextCheckpointApplicationService(
                new CompactionPolicy(0.70), request -> { throw new IllegalStateException("provider down"); }, repository);

        assertTrue(service.compact(session, UUID.randomUUID(), 2, new ContextUsage(800, 1_000),
                CompactionBarrier.clear(), "context", checkpoint(session, 1).exactTail(),
                checkpoint(session, 1).snapshotReferences()).isEmpty());
    }

    @Test
    void provider_token_estimator_accepts_runtime_provider_ids() {
        ProviderTokenEstimator estimator = new ProviderTokenEstimator(Map.of("ollama", 8_192, "openai", 128_000));

        assertEquals(8_192, estimator.limit("ollama"));
        assertEquals(128_000, estimator.limit("openai"));
    }

    private static GmContextCheckpoint checkpoint(UUID session, long version) {
        UUID planRevision = UUID.randomUUID();
        return GmContextCheckpoint.create(session, UUID.randomUUID(), version,
                new ContextSummaryCandidate("summary " + version, List.of("threat"), planRevision, version),
                new ExactTail("input", "scene", "response", "turn", "round", "location", "map", "fog", "choice"),
                new SnapshotReferences(planRevision, version, version, version, version, version));
    }
}
