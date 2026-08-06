package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.ExactTail;
import com.dndmaster.adventure.domain.runtime.checkpoint.SnapshotReferences;
import java.util.Objects;
import java.util.UUID;

public record ContextCompactionRequest(UUID sessionId, UUID sourceTurnId, String context,
                                       ExactTail exactTail, SnapshotReferences snapshotReferences) {
    public ContextCompactionRequest {
        sessionId = Objects.requireNonNull(sessionId); sourceTurnId = Objects.requireNonNull(sourceTurnId);
        context = Objects.requireNonNull(context); exactTail = Objects.requireNonNull(exactTail);
        snapshotReferences = Objects.requireNonNull(snapshotReferences);
    }
}
