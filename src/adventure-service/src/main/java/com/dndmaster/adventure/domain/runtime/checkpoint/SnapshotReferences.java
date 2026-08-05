package com.dndmaster.adventure.domain.runtime.checkpoint;

import java.util.UUID;

public record SnapshotReferences(UUID planRevisionId, long factVersion, long clockVersion, long characterVersion,
                                 long mapVersion, long fogVersion) {
    public SnapshotReferences {
        if (planRevisionId == null || factVersion < 0 || clockVersion < 0 || characterVersion < 0 || mapVersion < 0 || fogVersion < 0) {
            throw new IllegalArgumentException("invalid snapshot references");
        }
    }
}
