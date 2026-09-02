package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.UUID;

public interface RuntimeTurnFailureRepository {
    void append(RuntimeTurnFailureArtifact failure);
    List<RuntimeTurnFailureArtifact> findByTurnId(UUID turnId);
}
