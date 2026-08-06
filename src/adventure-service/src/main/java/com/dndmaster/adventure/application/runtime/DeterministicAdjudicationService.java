package com.dndmaster.adventure.application.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Idempotent boundary for deterministic rules/state resolution. */
public final class DeterministicAdjudicationService {
    private final Function<DeterministicAdjudicationRequest, AuthoritativeResolution> resolver;
    private final Map<java.util.UUID, Entry> journal = new ConcurrentHashMap<>();

    public DeterministicAdjudicationService(
            Function<DeterministicAdjudicationRequest, AuthoritativeResolution> resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    public AuthoritativeResolution resolve(DeterministicAdjudicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Entry entry = journal.compute(request.commandId(), (ignored, existing) -> {
            if (existing != null) {
                if (!existing.fingerprint().equals(request.fingerprint())) {
                    throw new IllegalStateException("adjudication command id reused with different input");
                }
                return existing;
            }
            AuthoritativeResolution resolution = Objects.requireNonNull(
                    resolver.apply(request), "resolution must not be null");
            return new Entry(request.fingerprint(), resolution);
        });
        return entry.resolution();
    }

    private record Entry(String fingerprint, AuthoritativeResolution resolution) { }
}
