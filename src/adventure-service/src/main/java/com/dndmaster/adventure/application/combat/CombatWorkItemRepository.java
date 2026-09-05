package com.dndmaster.adventure.application.combat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CombatWorkItemRepository {
    void enqueue(CombatWorkItem workItem);
    Optional<CombatWorkItem> claim(String workerId, Duration lease, Instant now);
    void save(CombatWorkItem workItem);
    Optional<CombatWorkItem> findByOperationId(UUID operationId);
    default Optional<CombatWorkItem> findFailedByEncounterId(UUID encounterId) { return Optional.empty(); }

    default boolean hasPendingForEncounter(UUID encounterId) { return false; }

    default Optional<CombatWorkItem> claim(String workerId, Duration lease) {
        return claim(workerId, lease, Instant.now());
    }
}
