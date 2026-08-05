package com.dndmaster.adventure.domain.runtime.checkpoint;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GmContextCheckpoint(UUID checkpointId, UUID sessionId, UUID sourceTurnId, long version, String summary,
                                  List<String> unresolvedThreats, UUID planRevisionId, long planVersion,
                                  ExactTail exactTail, SnapshotReferences snapshotReferences, Instant createdAt) {
    public GmContextCheckpoint {
        checkpointId = Objects.requireNonNull(checkpointId); sessionId = Objects.requireNonNull(sessionId);
        sourceTurnId = Objects.requireNonNull(sourceTurnId);
        if (version < 1 || summary == null || summary.isBlank()) throw new IllegalArgumentException("invalid checkpoint");
        unresolvedThreats = List.copyOf(Objects.requireNonNull(unresolvedThreats));
        planRevisionId = Objects.requireNonNull(planRevisionId); exactTail = Objects.requireNonNull(exactTail);
        snapshotReferences = Objects.requireNonNull(snapshotReferences); createdAt = Objects.requireNonNull(createdAt);
    }
    public static GmContextCheckpoint create(UUID sessionId, UUID sourceTurnId, long version,
                                              ContextSummaryCandidate candidate, ExactTail exactTail,
                                              SnapshotReferences refs) {
        Objects.requireNonNull(candidate); Objects.requireNonNull(refs);
        if (!candidate.planRevisionId().equals(refs.planRevisionId())) throw new IllegalArgumentException("plan reference mismatch");
        return new GmContextCheckpoint(UUID.randomUUID(), sessionId, sourceTurnId, version, candidate.summary(),
                candidate.unresolvedThreats(), candidate.planRevisionId(), candidate.planVersion(), exactTail, refs, Instant.now());
    }
}
