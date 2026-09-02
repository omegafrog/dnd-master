package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryRuntimeTurnFailureRepository implements RuntimeTurnFailureRepository {
    private final List<RuntimeTurnFailureArtifact> failures = new CopyOnWriteArrayList<>();
    @Override public void append(RuntimeTurnFailureArtifact failure) { failures.add(failure); }
    @Override public List<RuntimeTurnFailureArtifact> findByTurnId(UUID turnId) {
        return failures.stream().filter(failure -> failure.turnId().equals(turnId)).toList();
    }
}
