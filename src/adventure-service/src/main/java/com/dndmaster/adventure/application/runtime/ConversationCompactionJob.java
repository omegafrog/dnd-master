package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable, per-adventure request to summarize confirmed conversation that is no longer recent. */
public record ConversationCompactionJob(UUID id, AdventureId adventureId, long sourceStart, long sourceEnd,
                                       long expectedAdventureVersion, String idempotencyKey, Status status,
                                       Instant availableAt, Instant leaseUntil, int attempts) {
    public enum Status { READY, LEASED, RETRY_WAIT, DONE, MANUAL_REVIEW }

    public ConversationCompactionJob {
        id = Objects.requireNonNull(id); adventureId = Objects.requireNonNull(adventureId);
        if (sourceStart < 0 || sourceEnd < sourceStart) throw new IllegalArgumentException("invalid conversation source range");
        if (expectedAdventureVersion < 0) throw new IllegalArgumentException("expected adventure version must not be negative");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotency key must not be blank");
        status = Objects.requireNonNull(status); availableAt = Objects.requireNonNull(availableAt);
        if (attempts < 0) throw new IllegalArgumentException("attempts must not be negative");
    }

    public static ConversationCompactionJob ready(AdventureId adventureId, long sourceStart, long sourceEnd,
                                                   long version, Instant availableAt) {
        String key = adventureId.value() + ":" + version + ":" + sourceStart + ":" + sourceEnd;
        return new ConversationCompactionJob(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)), adventureId,
                sourceStart, sourceEnd, version, key, Status.READY, availableAt, null, 0);
    }
    public ConversationCompactionJob lease(Instant until) { return new ConversationCompactionJob(id, adventureId, sourceStart, sourceEnd, expectedAdventureVersion, idempotencyKey, Status.LEASED, availableAt, until, attempts + 1); }
    public ConversationCompactionJob retryAt(Instant when) { return new ConversationCompactionJob(id, adventureId, sourceStart, sourceEnd, expectedAdventureVersion, idempotencyKey, Status.RETRY_WAIT, when, null, attempts); }
    public ConversationCompactionJob done() { return new ConversationCompactionJob(id, adventureId, sourceStart, sourceEnd, expectedAdventureVersion, idempotencyKey, Status.DONE, availableAt, null, attempts); }
    public ConversationCompactionJob manualReview() { return new ConversationCompactionJob(id, adventureId, sourceStart, sourceEnd, expectedAdventureVersion, idempotencyKey, Status.MANUAL_REVIEW, availableAt, null, attempts); }
}
