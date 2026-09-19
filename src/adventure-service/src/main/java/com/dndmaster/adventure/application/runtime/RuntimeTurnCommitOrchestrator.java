package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.CombatMapMoveResult;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Executes a RuntimeTurn's external mutations forward, then invokes the local commit boundary. */
public final class RuntimeTurnCommitOrchestrator {
    private final RuntimeTurnRepository turnRepository;
    private final RuntimeTurnCommandRepository commandRepository;
    private final RuntimeTurnCommandAdapter commandAdapter;
    private final MovementFollowUpPort followUpPort;
    private final MovementFollowUpRuntimeConsumer followUpConsumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RuntimeTurnCommitOrchestrator(RuntimeTurnRepository turnRepository,
            RuntimeTurnCommandRepository commandRepository, RuntimeTurnCommandAdapter commandAdapter,
            MovementFollowUpPort followUpPort, MovementFollowUpRuntimeConsumer followUpConsumer) {
        this.turnRepository = Objects.requireNonNull(turnRepository, "turn repository must not be null");
        this.commandRepository = Objects.requireNonNull(commandRepository, "command repository must not be null");
        this.commandAdapter = Objects.requireNonNull(commandAdapter, "command adapter must not be null");
        this.followUpPort = Objects.requireNonNull(followUpPort, "movement follow-up port must not be null");
        this.followUpConsumer = Objects.requireNonNull(followUpConsumer, "movement follow-up consumer must not be null");
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
        if (turn.lifecycle() == RuntimeTurnLifecycle.COMMITTED) {
            return new Result(Status.COMMITTED, turn, null, movementResultForTurn(turnId));
        }
        if (turn.lifecycle() == RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED) {
            return new Result(Status.REPAIR_REQUIRED, turn, failedCommand(turnId), movementResultForTurn(turnId));
        }
        if (turn.lifecycle() != RuntimeTurnLifecycle.COMMITTING) {
            throw new IllegalStateException("turn is not committing: " + turn.lifecycle());
        }

        CombatMapMoveResult movementResult = null;
        int nextExecutionOrder = commandRepository.findByTurnId(turnId).stream()
                .mapToInt(RuntimeTurnCommand::executionOrder).max().orElse(-1) + 1;
        for (RuntimeTurnCommand command : commandRepository.findByTurnId(turnId).stream()
                .sorted(Comparator.comparingInt(RuntimeTurnCommand::executionOrder)
                        .thenComparing(RuntimeTurnCommand::commandId)).toList()) {
            movementResult = restoreMovementResult(command).orElse(movementResult);
            if (command.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) {
                if (movementResult != null && movementResult.followUp() != null
                        && "combat-map.move".equals(command.commandType())
                        && commandRepository.findByCommandId(movementResult.followUp().commandId()).isEmpty()) {
                    RuntimeTurnCommand followUp = followUpCommand(command, movementResult.followUp(), nextExecutionOrder++);
                    commandRepository.save(followUp);
                    RuntimeTurnCommandExecution followUpExecution = executeFollowUp(followUp);
                    if (followUpExecution.status() != RuntimeTurnCommandExecution.Status.DONE) {
                        RuntimeTurnCommand failedFollowUp = followUp.failed(followUpExecution.value());
                        commandRepository.save(failedFollowUp);
                        if (followUpExecution.status() == RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE) {
                            RuntimeTurn repaired = turnRepository.findByTurnId(turnId).orElse(turn).markCommitRepairRequired();
                            turnRepository.save(repaired);
                            return new Result(Status.REPAIR_REQUIRED, repaired, failedFollowUp, movementResult);
                        }
                        return new Result(Status.RETRY_REQUIRED, turnRepository.findByTurnId(turnId).orElse(turn), failedFollowUp, movementResult);
                    }
                    commandRepository.save(followUp.done(followUpExecution.value()));
                }
                continue;
            }
            RuntimeTurnCommandExecution execution;
            try {
                execution = "movement.follow-up".equals(command.commandType())
                        ? executeFollowUp(command)
                        : Objects.requireNonNull(commandAdapter.execute(command), "command adapter result must not be null");
            } catch (RuntimeException failure) {
                execution = RuntimeTurnCommandExecution.transientFailure(failure.getMessage());
            }
            if (execution.movementResult() != null) movementResult = execution.movementResult();
            if (execution.status() == RuntimeTurnCommandExecution.Status.DONE) {
                commandRepository.save(command.done(execution.value()));
                if (movementResult != null && movementResult.followUp() != null
                        && "combat-map.move".equals(command.commandType())
                        && commandRepository.findByCommandId(movementResult.followUp().commandId()).isEmpty()) {
                    RuntimeTurnCommand followUp = followUpCommand(command, movementResult.followUp(), nextExecutionOrder++);
                    commandRepository.save(followUp);
                    RuntimeTurnCommandExecution followUpExecution = executeFollowUp(followUp);
                    if (followUpExecution.status() != RuntimeTurnCommandExecution.Status.DONE) {
                        RuntimeTurnCommand failedFollowUp = followUp.failed(followUpExecution.value());
                        commandRepository.save(failedFollowUp);
                        if (followUpExecution.status() == RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE) {
                            RuntimeTurn repaired = turnRepository.findByTurnId(turnId).orElse(turn).markCommitRepairRequired();
                            turnRepository.save(repaired);
                            return new Result(Status.REPAIR_REQUIRED, repaired, failedFollowUp, movementResult);
                        }
                        return new Result(Status.RETRY_REQUIRED, turnRepository.findByTurnId(turnId).orElse(turn), failedFollowUp, movementResult);
                    }
                    commandRepository.save(followUp.done(followUpExecution.value()));
                }
                continue;
            }
            RuntimeTurnCommand failed = command.failed(
                    execution.movementResult() == null ? execution.value() : execution.movementResult().status().name(),
                    execution.value());
            commandRepository.save(failed);
            if (execution.status() == RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE) {
                RuntimeTurn repaired = turnRepository.findByTurnId(turnId).orElse(turn)
                        .markCommitRepairRequired();
                turnRepository.save(repaired);
                return new Result(Status.REPAIR_REQUIRED, repaired, failed, movementResult);
            }
            return new Result(Status.RETRY_REQUIRED, turnRepository.findByTurnId(turnId).orElse(turn), failed, movementResult);
        }

        // The callback is deliberately last. If it fails, the turn remains
        // COMMITTING and a later resume skips already-DONE external commands.
        localAdventureCommit.run();
        RuntimeTurn committed = turnRepository.findByTurnId(turnId).orElse(turn).markSafeCommitted();
        turnRepository.save(committed);
        return new Result(Status.COMMITTED, committed, null, movementResult);
    }

    private RuntimeTurnCommandExecution executeFollowUp(RuntimeTurnCommand command) {
        try {
            MovementFollowUpCommand followUp = objectMapper.readValue(command.payloadJson(), MovementFollowUpCommand.class);
            MovementFollowUpPort.Result result = followUpPort.publish(followUp, command.adventureId(), command.sessionId(), command.ownerPlayerId());
            if (result.status() == MovementFollowUpPort.Result.Status.DONE) {
                result = followUpConsumer.consume(command, followUp);
            }
            return switch (result.status()) {
                case DONE -> RuntimeTurnCommandExecution.done(result.value());
                case RETRY -> RuntimeTurnCommandExecution.transientFailure(result.value());
                case PERMANENT_FAILURE -> RuntimeTurnCommandExecution.permanentFailure(result.value());
            };
        } catch (Exception failure) {
            return RuntimeTurnCommandExecution.transientFailure(failure.getMessage());
        }
    }

    private RuntimeTurnCommand followUpCommand(RuntimeTurnCommand source, MovementFollowUpCommand followUp, int order) {
        try {
            return RuntimeTurnCommand.create(source.turnId(), followUp.commandId(), source.adventureId(), source.sessionId(),
                    source.ownerPlayerId(), source.targetContext(), "movement.follow-up", objectMapper.writeValueAsString(followUp), order);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("movement follow-up persistence failed", failure);
        }
    }

    /** Replays a stored map result for reconnects and duplicate turn requests. */
    public CombatMapMoveResult movementResultForTurn(UUID turnId) {
        return commandRepository.findByTurnId(Objects.requireNonNull(turnId, "turn id must not be null")).stream()
                .sorted(Comparator.comparingInt(RuntimeTurnCommand::executionOrder)
                        .thenComparing(RuntimeTurnCommand::commandId))
                .map(this::restoreMovementResult)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElse(null);
    }

    private java.util.Optional<CombatMapMoveResult> restoreMovementResult(RuntimeTurnCommand command) {
        if (!"combat-map.move".equals(command.commandType()) || command.outcomeJson().isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(objectMapper.readValue(command.outcomeJson(), CombatMapMoveResult.class));
        } catch (java.io.IOException | RuntimeException ignored) {
            return java.util.Optional.empty();
        }
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
    public record Result(Status status, RuntimeTurn turn, RuntimeTurnCommand failedCommand,
            com.dndmaster.adventure.application.combat.CombatMapMoveResult movementResult) {
        public Result(Status status, RuntimeTurn turn, RuntimeTurnCommand failedCommand) {
            this(status, turn, failedCommand, null);
        }
    }
}
