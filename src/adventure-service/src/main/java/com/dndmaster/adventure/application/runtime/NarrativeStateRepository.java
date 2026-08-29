package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import java.util.Optional;
import java.util.UUID;

public interface NarrativeStateRepository {
    Optional<NarrativeState> findBySessionId(UUID sessionId);
    void save(UUID sessionId, NarrativeState state);
}
