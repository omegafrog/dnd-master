package com.dndmaster.adventure.application.runtime;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Executes a RuntimeTurn's external mutations forward, then invokes the local commit boundary. */
public final class RuntimeTurnCommitOrchestrator {
    private final RuntimeTurnRepository turnRepository;
    private final RuntimeTurnCommandRepository commandRepository;
    private final RuntimeTurnCommandAdapter commandAdapter;

    public RuntimeTurnCommitOrchestrator(RuntimeTurnRepository turnRepository,
            RuntimeTurnCommandRepository commandRepository, RuntimeTurnCommandAdapter commandAdapter) {
        this.turnRepository = Objects.requireNonNull(turnRepository, "turn repository must not be null");
        this.commandRepository = Objects.requireNonNull(commandRepository, "command repository must not be null");
        this.commandAdapter = Objects.requireNonNull(commandAdapter, "command adapter must not be null");
    }

    public Result commit(RuntimeTurn readyTurn, List<RuntimeTurnCommand> commands, Runnable localAdventureCommit) {
        Objects.requireNonNull(readyTurn, "ready turn must not be null");
        Objects.requireNonNull(commands, "runtime commands must not be null");
        Objects.requireNonNull(localAdventureCommit, "local adventure commit must not be null");
        if (readyTurn.lifecycle() == RuntimeTurnLifecycle.READY_TO_COMMIT) {
            validateCommands(readyTurn.turnId(), commands);
            turnRepository.save(readyTurn.beginCommit());
            commandRepository.saveAll(commands);
        } else if (readyTurn.lifecycle() != RuntimeTurnLifecycle.COMMITTING) {
            throw new IllegalStateException("turn is not ready to commit: " + readyTurn.lifecycle());
        }
        return resume(readyTurn.turnId(), localAdventureCommit);
    }

    public Result resume(UUID turnId, Runnable localAdventureCommit) {
        Objects.requireNonNull(turnId, "turn id must not be null");
        Objects.requireNonNull(localAdventureCommit, "local adventure commit must not be null");
        RuntimeTurn turn = turnRepository.findByTurnId(turnId)
                .orElseThrow(() -> new IllegalStateException("runtime turn not found"));
        if (turn.lifecycle() == RuntimeTurnLifecycle.COMMITTED) return new Result(Status.COMMITTED, turn, null);
        if (turn.lifecycle() == RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED) {
            return new Result(Status.REPAIR_REQUIRED, turn, failedCommand(turnId));
        }
        if (turn.lifecycle() != RuntimeTurnLifecycle.COMMITTING) {
            throw new IllegalStateException("turn is not committing: " + turn.lifecycle());
        }

        for (RuntimeTurnCommand command : commandRepository.findByTurnId(turnId).stream()
                .sorted(Comparator.comparingInt(RuntimeTurnCommand::executionOrder)
                        .thenComparing(RuntimeTurnCommand::commandId)).toList()) {
            if (command.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) continue;
            RuntimeTurnCommandExecution execution;
            try {
                execution = Objects.requireNonNull(commandAdapter.execute(command), "command adapter result must not be null");
            } catch (RuntimeException failure) {
                execution = RuntimeTurnCommandExecution.transientFailure(failure.getMessage());
            }
            if (execution.status() == RuntimeTurnCommandExecution.Status.DONE) {
                commandRepository.save(command.done(execution.value()));
                continue;
            }
            RuntimeTurnCommand failed = command.failed(execution.value());
            commandRepository.save(failed);
            if (execution.status() == RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE) {
                RuntimeTurn repaired = turnRepository.findByTurnId(turnId).orElse(turn)
                        .markCommitRepairRequired();
                turnRepository.save(repaired);
                return new Result(Status.REPAIR_REQUIRED, repaired, failed);
            }
            return new Result(Status.RETRY_REQUIRED, turnRepository.findByTurnId(turnId).orElse(turn), failed);
        }

        // The callback is deliberately last. If it fails, the turn remains
        // COMMITTING and a later resume skips already-DONE external commands.
        localAdventureCommit.run();
        RuntimeTurn committed = turnRepository.findByTurnId(turnId).orElse(turn).markSafeCommitted();
        turnRepository.save(committed);
        return new Result(Status.COMMITTED, committed, null);
    }

    private void validateCommands(UUID turnId, List<RuntimeTurnCommand> commands) {
        java.util.Set<Integer> orders = new java.util.HashSet<>();
        java.util.Set<String> idempotencyKeys = new java.util.HashSet<>();
        for (RuntimeTurnCommand command : commands) {
            if (!command.turnId().equals(turnId)) throw new IllegalArgumentException("command belongs to another turn");
            if (!orders.add(command.executionOrder())) throw new IllegalArgumentException("duplicate command execution order");
            if (!idempotencyKeys.add(command.idempotencyKey())) throw new IllegalArgumentException("duplicate command idempotency key");
        }
    }

    private RuntimeTurnCommand failedCommand(UUID turnId) {
        return commandRepository.findByTurnId(turnId).stream()
                .filter(command -> command.executionStatus() == RuntimeTurnCommand.ExecutionStatus.FAILED)
                .sorted(Comparator.comparingInt(RuntimeTurnCommand::executionOrder)).findFirst().orElse(null);
    }

    public enum Status { COMMITTED, RETRY_REQUIRED, REPAIR_REQUIRED }
    public record Result(Status status, RuntimeTurn turn, RuntimeTurnCommand failedCommand) { }
}
