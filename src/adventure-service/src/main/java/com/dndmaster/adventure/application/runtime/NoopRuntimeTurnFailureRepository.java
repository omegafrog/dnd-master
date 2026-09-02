package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.UUID;

final class NoopRuntimeTurnFailureRepository implements RuntimeTurnFailureRepository {
    @Override public void append(RuntimeTurnFailureArtifact failure) {}
    @Override public List<RuntimeTurnFailureArtifact> findByTurnId(UUID turnId) { return List.of(); }
}
