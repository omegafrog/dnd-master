package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail;
import com.dndmaster.adventure.domain.runtime.checkpoint.ContextSummaryCandidate;
import com.dndmaster.adventure.domain.runtime.checkpoint.GmContextCheckpoint;
import com.dndmaster.adventure.domain.runtime.checkpoint.SnapshotReferences;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class GmContextCheckpointApplicationService {
    private final CompactionPolicy policy;
    private final ContextCompactionPort compactionPort;
    private final GmContextCheckpointRepository repository;

    public GmContextCheckpointApplicationService(CompactionPolicy policy, ContextCompactionPort compactionPort,
                                                  GmContextCheckpointRepository repository) {
        this.policy = Objects.requireNonNull(policy); this.compactionPort = Objects.requireNonNull(compactionPort);
        this.repository = Objects.requireNonNull(repository);
    }

    public Optional<GmContextCheckpoint> compact(UUID sessionId, UUID sourceTurnId, long version,
                                                  ContextUsage usage, CompactionBarrier barrier, String context,
                                                  ExactTail exactTail, SnapshotReferences references) {
        if (!policy.shouldSchedule(usage, false) || !policy.canCompact(barrier)) return Optional.empty();
        try {
            ContextSummaryCandidate candidate = Objects.requireNonNull(compactionPort.summarize(
                    new ContextCompactionRequest(sessionId, sourceTurnId, context, exactTail, references)));
            long checkpointVersion = repository.current(sessionId).map(current -> current.version() + 1).orElse(1L);
            GmContextCheckpoint checkpoint = GmContextCheckpoint.create(sessionId, sourceTurnId, checkpointVersion,
                    candidate, exactTail, references);
            repository.append(checkpoint);
            return Optional.of(checkpoint);
        } catch (RuntimeException failure) {
            // Previous current checkpoint remains authoritative. Compaction is internal and retryable.
            return repository.current(sessionId);
        }
    }
}
