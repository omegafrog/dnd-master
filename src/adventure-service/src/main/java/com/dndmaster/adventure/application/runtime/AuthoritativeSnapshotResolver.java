package com.dndmaster.adventure.application.runtime;

import java.util.UUID;

@FunctionalInterface
public interface AuthoritativeSnapshotResolver {
    VersionedRuntimeSnapshots resolve(UUID sessionId);
}
