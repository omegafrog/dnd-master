package com.dndmaster.adventure.application.scenario.compilation;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface WorkQueuePort {
    void enqueue(WorkEnvelope work);
    Optional<Delivery> claim(String workerId, Duration lease);
    void acknowledge(Delivery delivery);
    void retry(Delivery delivery, String reason);

    record Delivery(WorkEnvelope work, UUID deliveryToken, String workerId) {
        public Delivery {
            work = Objects.requireNonNull(work, "work must not be null");
            deliveryToken = Objects.requireNonNull(deliveryToken, "delivery token must not be null");
            workerId = Objects.requireNonNull(workerId, "worker id must not be null");
        }
    }
}
