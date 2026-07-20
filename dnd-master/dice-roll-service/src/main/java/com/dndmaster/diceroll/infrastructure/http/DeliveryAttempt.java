package com.dndmaster.diceroll.infrastructure.http;

public record DeliveryAttempt(DeliveryStatus status, long version, boolean shouldDeliver) {
    public DeliveryAttempt {
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
    }
}
