package com.dndmaster.adventure.domain.combat;

import java.util.Objects;
import java.util.UUID;

/** Durable player-safe event; internal combat payloads never cross this boundary. */
public record CombatEvent(UUID encounterId, long sequence, String eventType, String playerPayload) {
    public CombatEvent {
        Objects.requireNonNull(encounterId); Objects.requireNonNull(eventType); Objects.requireNonNull(playerPayload);
        if (sequence < 1) throw new IllegalArgumentException("combat event sequence must be positive");
    }
}
