package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class RuntimeCommandSagaApplicationService {
    private final RuntimeCommandJournal journal;
    public RuntimeCommandSagaApplicationService(RuntimeCommandJournal journal) { this.journal = Objects.requireNonNull(journal); }

    public RuntimeCommandOutcome execute(RuntimeCommandRequest request, Function<RuntimeCommandRequest, RuntimeCommandOutcome> dispatcher) {
        Objects.requireNonNull(request); Objects.requireNonNull(dispatcher);
        RuntimeCommandJournalEntry existing = journal.find(request.commandId()).orElse(null);
        if (existing != null) {
            if (!existing.fingerprint().equals(request.fingerprint())) throw new CommandFingerprintConflictException();
            if (existing.status() == RuntimeCommandStatus.APPLIED || existing.status() == RuntimeCommandStatus.REJECTED) return existing.outcome();
        } else {
            journal.record(new RuntimeCommandJournalEntry(request.commandId(), request.sessionId(), request.turnId(), request.ownerPlayerId(), request.toolName(), request.fingerprint(), RuntimeCommandStatus.PENDING, null, 0));
        }
        try {
            RuntimeCommandOutcome outcome = Objects.requireNonNull(dispatcher.apply(request));
            journal.record(existingOrPending(request).with(outcome.status(), outcome));
            return outcome;
        } catch (RuntimeException failure) {
            RuntimeCommandJournalEntry pending = existingOrPending(request);
            journal.record(pending.with(RuntimeCommandStatus.UNKNOWN, null));
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

    private RuntimeCommandJournalEntry existingOrPending(RuntimeCommandRequest request) {
        return journal.find(request.commandId()).orElseThrow();
    }
}
