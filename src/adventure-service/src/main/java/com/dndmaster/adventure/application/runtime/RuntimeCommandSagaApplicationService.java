package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class RuntimeCommandSagaApplicationService {
    private final RuntimeCommandJournal journal;
    private final GmQualityMetrics metrics;
    public RuntimeCommandSagaApplicationService(RuntimeCommandJournal journal) { this(journal, new GmQualityMetrics() { public void record(GmQualityGateReport report) {} }); }
    public RuntimeCommandSagaApplicationService(RuntimeCommandJournal journal, GmQualityMetrics metrics) { this.journal = Objects.requireNonNull(journal); this.metrics = Objects.requireNonNull(metrics); }

    public RuntimeCommandOutcome execute(RuntimeCommandRequest request, Function<RuntimeCommandRequest, RuntimeCommandOutcome> dispatcher) {
        Objects.requireNonNull(request); Objects.requireNonNull(dispatcher);
        RuntimeCommandJournalEntry existing = journal.find(request.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(request.fingerprint())) throw new CommandFingerprintConflictException();
            if (existing.status() == RuntimeCommandStatus.APPLIED || existing.status() == RuntimeCommandStatus.REJECTED) return existing.outcome();
        }
        RuntimeCommandJournalEntry pending = new RuntimeCommandJournalEntry(request.commandId(), request.sessionId(), request.turnId(), request.ownerPlayerId(), request.toolName(), request.fingerprint(), RuntimeCommandStatus.PENDING, null, 0);
        if (!journal.claim(pending)) throw new CommandInProgressException();
        metrics.recordSagaPending();
        try {
            RuntimeCommandOutcome outcome = Objects.requireNonNull(dispatcher.apply(request));
            journal.record(journal.find(request.commandId()).orElseThrow().with(outcome.status(), outcome));
            metrics.recordSagaCompleted();
            return outcome;
        } catch (RuntimeException failure) {
            journal.record(journal.find(request.commandId()).orElseThrow().with(RuntimeCommandStatus.UNKNOWN, null));
            metrics.recordSagaCompleted();
            throw failure;
        }
    }

    public RuntimeCommandOutcome resume(UUID commandId, Function<RuntimeCommandRequest, RuntimeCommandOutcome> dispatcher,
                                        Function<UUID, RuntimeCommandOutcome> outcomeQuery) {
        RuntimeCommandJournalEntry entry = journal.find(Objects.requireNonNull(commandId)).orElseThrow();
        if (entry.status() == RuntimeCommandStatus.APPLIED || entry.status() == RuntimeCommandStatus.REJECTED) return entry.outcome();
        RuntimeCommandOutcome recovered = Objects.requireNonNull(outcomeQuery.apply(commandId));
        journal.record(entry.with(recovered.status(), recovered));
        return recovered;
    }

}
