package com.dndmaster.adventure.application.runtime;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Idempotent boundary for deterministic rules/state resolution. */
public final class DeterministicAdjudicationService {
    private final Function<DeterministicAdjudicationRequest, AuthoritativeResolution> resolver;
    private final RuntimeCommandJournal journal;
    private final ObjectMapper mapper;

    public DeterministicAdjudicationService(
            Function<DeterministicAdjudicationRequest, AuthoritativeResolution> resolver) {
        this(new InMemoryRuntimeCommandJournal(), new ObjectMapper(), resolver);
    }

    public DeterministicAdjudicationService(RuntimeCommandJournal journal, ObjectMapper mapper,
            Function<DeterministicAdjudicationRequest, AuthoritativeResolution> resolver) {
        this.journal = Objects.requireNonNull(journal, "journal must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    public AuthoritativeResolution resolve(DeterministicAdjudicationRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        RuntimeCommandJournalEntry existing = journal.find(request.commandId()).orElse(null);
        if (existing != null) return existingResolution(request, existing);
        RuntimeCommandJournalEntry pending = new RuntimeCommandJournalEntry(request.commandId(), request.sessionId(),
                request.turnId(), request.ownerPlayerId(), "adjudication.resolve", request.fingerprint(),
                RuntimeCommandStatus.PENDING, null, 0);
        if (!journal.claim(pending)) {
            return journal.find(request.commandId()).map(entry -> existingResolution(request, entry))
                    .orElseThrow(() -> new IllegalStateException("adjudication command is in progress"));
        }
        AuthoritativeResolution resolution = Objects.requireNonNull(resolver.apply(request), "resolution must not be null");
        journal.record(pending.with(RuntimeCommandStatus.APPLIED,
                RuntimeCommandOutcome.applied(encode(resolution), request.expectedVersion() + 1)));
        return resolution;
    }

    private AuthoritativeResolution existingResolution(DeterministicAdjudicationRequest request,
            RuntimeCommandJournalEntry existing) {
        if (!existing.fingerprint().equals(request.fingerprint())) {
            throw new IllegalStateException("adjudication command id reused with different input");
        }
        if (existing.status() == RuntimeCommandStatus.PENDING || existing.outcome() == null) {
            throw new IllegalStateException("adjudication command is in progress");
        }
        try {
            return mapper.readValue(existing.outcome().value(), AuthoritativeResolution.class);
        } catch (Exception failure) {
            throw new IllegalStateException("invalid persisted adjudication outcome", failure);
        }
    }

    private String encode(AuthoritativeResolution resolution) {
        try { return mapper.writeValueAsString(resolution); }
        catch (Exception failure) { throw new IllegalStateException("cannot persist adjudication outcome", failure); }
    }
}
