package com.dndmaster.adventure.application.combat;

import java.util.Objects;

/** Durable step identity used to resume an external command without rerunning it. */
public record CombatActionStep(String name, String idempotencyKey, Status status) {
    public enum Status { PENDING, DONE, FAILED }

    public CombatActionStep {
        Objects.requireNonNull(name, "step name must not be null");
        Objects.requireNonNull(idempotencyKey, "step idempotency key must not be null");
        Objects.requireNonNull(status, "step status must not be null");
    }
}
