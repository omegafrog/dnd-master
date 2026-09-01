package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.util.function.Function;
import java.util.Optional;

public final class RuntimeCommandSagaApplicationService {
    private final RuntimeCommandJournal journal;
    private final GmQualityMetrics metrics;
    public RuntimeCommandSagaApplicationService(RuntimeCommandJournal journal) { this(journal, new GmQualityMetrics() { public void record(GmQualityGateReport report) {} }); }
    public RuntimeCommandSagaApplicationService(RuntimeCommandJournal journal, GmQualityMetrics metrics) { this.journal = Objects.requireNonNull(journal); this.metrics = Objects.requireNonNull(metrics); }

    public RuntimeCommandOutcome execute(RuntimeCommandRequest request, Function<RuntimeCommandRequest, RuntimeCommandOutcome> dispatcher) {
        return execute(request, dispatcher, ignored -> Optional.empty());
    }

    public RuntimeCommandOutcome execute(RuntimeCommandRequest request, Function<RuntimeCommandRequest, RuntimeCommandOutcome> dispatcher,
                                          Function<UUID, Optional<RuntimeCommandOutcome>> outcomeQuery) {
        Objects.requireNonNull(request); Objects.requireNonNull(dispatcher);
        RuntimeCommandJournalEntry existing = journal.find(request.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(request.fingerprint())) throw new CommandFingerprintConflictException();
            if ((existing.candidateId() != null && !Objects.equals(existing.candidateId(), request.candidateId()))
                    || (existing.toolIndex() != null && !Objects.equals(existing.toolIndex(), request.toolIndex()))) {
                throw new IllegalStateException("COMMAND_IDENTITY_MISMATCH");
            }
            if (existing.status() == RuntimeCommandStatus.PENDING
                    && journal.markUnknownIfStale(request.commandId(), Instant.now().minusSeconds(300))) {
                existing = journal.find(request.commandId()).orElse(existing);
            }
            if (existing.status() == RuntimeCommandStatus.UNKNOWN) {
                Optional<RuntimeCommandOutcome> recovered = outcomeQuery.apply(request.commandId());
                if (recovered.isPresent()) {
                    RuntimeCommandOutcome outcome = recovered.get();
                    journal.record(existing.with(outcome.status(), outcome));
                    return outcome;
                }
                throw new IllegalStateException("COMMAND_RECOVERY_REQUIRED");
            }
            if (existing.status() == RuntimeCommandStatus.APPLIED || existing.status() == RuntimeCommandStatus.REJECTED) return existing.outcome();
        }
        RuntimeCommandJournalEntry pending = new RuntimeCommandJournalEntry(request.commandId(), request.sessionId(), request.turnId(), request.ownerPlayerId(), request.toolName(), request.fingerprint(), RuntimeCommandStatus.PENDING, null, 0, request.candidateId(), request.toolIndex());
        if (!journal.claim(pending)) {
            // A concurrent retry may be completing the same invocation. Re-read
            // briefly before reporting contention so completed tool results are reused.
            for (int attempt = 0; attempt < 5; attempt++) {
                RuntimeCommandJournalEntry concurrent = journal.find(request.commandId()).orElse(null);
                if (concurrent != null && (concurrent.status() == RuntimeCommandStatus.APPLIED
                        || concurrent.status() == RuntimeCommandStatus.REJECTED)) return concurrent.outcome();
                try { Thread.sleep(20L); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CommandInProgressException();
                }
            }
            throw new CommandInProgressException();
        }
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
        metrics.recordSagaCompleted();
        return recovered;
    }

}
