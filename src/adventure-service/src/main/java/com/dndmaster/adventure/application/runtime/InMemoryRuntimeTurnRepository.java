package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small process-local repository used by policy tests and local adapters. */
public final class InMemoryRuntimeTurnRepository implements RuntimeTurnRepository {
    private final ConcurrentHashMap<UUID, RuntimeTurn> turns = new ConcurrentHashMap<>();

    @Override public Optional<RuntimeTurn> findByTurnId(UUID turnId) { return Optional.ofNullable(turns.get(turnId)); }
    @Override public Optional<RuntimeTurn> findByCommandId(UUID commandId) {
        return turns.values().stream().filter(turn -> turn.commandId().equals(commandId)).findFirst();
    }
    @Override public List<RuntimeTurn> findAllByAdventureId(AdventureId adventureId) {
        return turns.values().stream().filter(turn -> turn.adventureId().equals(adventureId)).toList();
    }
    @Override public List<RuntimeTurn> findAllByLifecycle(RuntimeTurnLifecycle lifecycle) {
        return turns.values().stream().filter(turn -> turn.lifecycle() == lifecycle).toList();
    }
    @Override public synchronized void save(RuntimeTurn turn) {
        boolean active = !turn.lifecycle().isCommitted()
                && turn.lifecycle() != RuntimeTurnLifecycle.DISCARDED
                && turn.lifecycle() != RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED;
        if (active && turns.values().stream().anyMatch(existing -> !existing.turnId().equals(turn.turnId())
                && existing.adventureId().equals(turn.adventureId())
                && !existing.lifecycle().isCommitted()
                && existing.lifecycle() != RuntimeTurnLifecycle.DISCARDED
                && existing.lifecycle() != RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED)) {
            throw new IllegalStateException("one active runtime turn per adventure");
        }
        turns.put(turn.turnId(), turn);
    }
}
