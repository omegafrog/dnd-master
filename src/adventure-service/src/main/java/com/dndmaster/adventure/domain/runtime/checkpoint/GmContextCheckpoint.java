package com.dndmaster.adventure.domain.runtime.checkpoint;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GmContextCheckpoint(UUID checkpointId, UUID sessionId, UUID sourceTurnId, long version, String summary,
                                  List<String> unresolvedThreats, UUID planRevisionId, long planVersion,
                                  ExactTail exactTail, SnapshotReferences snapshotReferences, Instant createdAt,
                                  String provider, String model, String reasoning) {
    public GmContextCheckpoint(UUID checkpointId, UUID sessionId, UUID sourceTurnId, long version, String summary,
                               List<String> unresolvedThreats, UUID planRevisionId, long planVersion,
                               ExactTail exactTail, SnapshotReferences snapshotReferences, Instant createdAt) {
        this(checkpointId, sessionId, sourceTurnId, version, summary, unresolvedThreats, planRevisionId, planVersion,
                exactTail, snapshotReferences, createdAt, "unknown", "unknown", "unknown");
    }
    public GmContextCheckpoint {
        checkpointId = Objects.requireNonNull(checkpointId); sessionId = Objects.requireNonNull(sessionId);
        sourceTurnId = Objects.requireNonNull(sourceTurnId);
        if (version < 1 || summary == null || summary.isBlank()) throw new IllegalArgumentException("invalid checkpoint");
        unresolvedThreats = List.copyOf(Objects.requireNonNull(unresolvedThreats));
        planRevisionId = Objects.requireNonNull(planRevisionId); exactTail = Objects.requireNonNull(exactTail);
        snapshotReferences = Objects.requireNonNull(snapshotReferences); createdAt = Objects.requireNonNull(createdAt);
        provider = required(provider, "provider"); model = required(model, "model"); reasoning = required(reasoning, "reasoning");
    }
    public static GmContextCheckpoint create(UUID sessionId, UUID sourceTurnId, long version,
                                              ContextSummaryCandidate candidate, ExactTail exactTail,
                                              SnapshotReferences refs) {
        return create(sessionId, sourceTurnId, version, candidate, exactTail, refs, "unknown", "unknown", "unknown");
    }
    public static GmContextCheckpoint create(UUID sessionId, UUID sourceTurnId, long version,
                                              ContextSummaryCandidate candidate, ExactTail exactTail,
                                              SnapshotReferences refs, String provider, String model, String reasoning) {
        Objects.requireNonNull(candidate); Objects.requireNonNull(refs);
        if (!candidate.planRevisionId().equals(refs.planRevisionId())) throw new IllegalArgumentException("plan reference mismatch");
        return new GmContextCheckpoint(UUID.randomUUID(), sessionId, sourceTurnId, version, candidate.summary(),
                candidate.unresolvedThreats(), candidate.planRevisionId(), candidate.planVersion(), exactTail, refs, Instant.now(),
                provider, model, reasoning);
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " required");
        return value.trim();
    }
}
