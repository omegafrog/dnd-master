package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.combat.CombatEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCombatEventRepository implements CombatEventRepository {
    private final ConcurrentHashMap<UUID, List<CombatEvent>> events = new ConcurrentHashMap<>();
    @Override public synchronized void append(CombatEvent event) {
        var current = events.computeIfAbsent(event.encounterId(), ignored -> new ArrayList<>());
        if (current.stream().anyMatch(existing -> existing.sequence() == event.sequence())) return;
        if (!current.isEmpty() && event.sequence() <= current.get(current.size() - 1).sequence()) {
            throw new IllegalStateException("combat event sequences must increase");
        }
        current.add(event);
    }
    @Override public List<CombatEvent> after(UUID encounterId, long sequence) {
        return events.getOrDefault(encounterId, List.of()).stream()
                .filter(event -> event.sequence() > sequence)
                .sorted(Comparator.comparingLong(CombatEvent::sequence)).toList();
    }
}
