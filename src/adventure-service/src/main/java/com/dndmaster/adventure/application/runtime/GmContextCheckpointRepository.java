package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.checkpoint.GmContextCheckpoint;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GmContextCheckpointRepository {
    Optional<GmContextCheckpoint> current(UUID sessionId);
    List<GmContextCheckpoint> history(UUID sessionId);
    void append(GmContextCheckpoint checkpoint);
}
