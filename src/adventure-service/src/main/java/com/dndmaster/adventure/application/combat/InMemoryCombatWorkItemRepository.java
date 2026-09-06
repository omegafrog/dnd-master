package com.dndmaster.adventure.application.combat;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Test/local implementation with the same lease semantics as the SQL adapter. */
public final class InMemoryCombatWorkItemRepository implements CombatWorkItemRepository {
    private final Map<UUID, CombatWorkItem> items = new LinkedHashMap<>();

    @Override public synchronized void enqueue(CombatWorkItem workItem) {
        items.putIfAbsent(workItem.workItemId(), workItem);
    }

    @Override public synchronized Optional<CombatWorkItem> claim(String workerId, Duration lease, Instant now) {
        return items.values().stream()
                .filter(item -> (item.status() == CombatWorkItem.Status.PENDING && !item.dueAt().isAfter(now))
                        || (item.status() == CombatWorkItem.Status.CLAIMED && item.leaseUntil() != null && !item.leaseUntil().isAfter(now)))
                .min(Comparator.comparing(CombatWorkItem::dueAt))
                .map(item -> {
                    CombatWorkItem claimed = item.claimed(UUID.randomUUID(), now.plus(lease));
                    items.put(item.workItemId(), claimed);
                    return claimed;
                });
    }

    @Override public synchronized void save(CombatWorkItem workItem) { items.put(workItem.workItemId(), workItem); }

    @Override public synchronized Optional<CombatWorkItem> findByOperationId(UUID operationId) {
        return items.values().stream().filter(item -> operationId.equals(item.operationId())).findFirst();
    }

    @Override public synchronized Optional<CombatWorkItem> findFailedByEncounterId(UUID encounterId) {
        return items.values().stream().filter(item -> item.encounterId().equals(encounterId)
                && item.status() == CombatWorkItem.Status.FAILED).findFirst();
    }

    public synchronized CombatWorkItem get(UUID id) { return Optional.ofNullable(items.get(id)).orElseThrow(); }
}
