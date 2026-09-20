package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.CombatMapMoveResult;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
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
                    RuntimeTurnCommand followUp;
                    try {
                        followUp = followUpCommand(command, movementResult.followUp(),
                                rawFollowUpPayload(command.outcomeJson(), movementResult.followUp()), nextExecutionOrder++);
                    } catch (PermanentFollowUpFailure failure) {
                        return repairRequired(turnId, turn, command, failure.getMessage(), movementResult);
                    }
                    commandRepository.save(followUp);
                    RuntimeTurnCommandExecution followUpExecution = executeFollowUp(followUp, movementResult);
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
                        ? executeFollowUp(command, movementResult)
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
                    RuntimeTurnCommand followUp;
                    try {
                        followUp = followUpCommand(command, movementResult.followUp(),
                                rawFollowUpPayload(execution.value(), movementResult.followUp()), nextExecutionOrder++);
                    } catch (PermanentFollowUpFailure failure) {
                        return repairRequired(turnId, turn, command, failure.getMessage(), movementResult);
                    }
                    commandRepository.save(followUp);
                    RuntimeTurnCommandExecution followUpExecution = executeFollowUp(followUp, movementResult);
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

    private RuntimeTurnCommandExecution executeFollowUp(RuntimeTurnCommand command,
            CombatMapMoveResult currentMovementResult) {
        try {
            MovementFollowUpCommand followUp = readDurableFollowUp(command);
            validateFollowUpIdentity(currentMovementResult, command.turnId(), followUp);
            MovementFollowUpPort.Result result = followUpPort.publish(followUp, command.adventureId(), command.sessionId(), command.ownerPlayerId());
            if (result.status() == MovementFollowUpPort.Result.Status.DONE) {
                result = followUpConsumer.consume(command, followUp);
            }
            return switch (result.status()) {
                case DONE -> RuntimeTurnCommandExecution.done(result.value());
                case RETRY -> RuntimeTurnCommandExecution.transientFailure(result.value());
                case PERMANENT_FAILURE -> RuntimeTurnCommandExecution.permanentFailure(result.value());
            };
        } catch (PermanentFollowUpFailure failure) {
            return RuntimeTurnCommandExecution.permanentFailure(failure.getMessage());
        } catch (CorruptMovementFollowUpException failure) {
            return RuntimeTurnCommandExecution.permanentFailure(failure.getMessage());
        } catch (RuntimeException failure) {
            return RuntimeTurnCommandExecution.transientFailure(failure.getMessage());
        }
    }

    private void validateFollowUpIdentity(CombatMapMoveResult movement, UUID turnId,
            MovementFollowUpCommand followUp) {
        if (movement == null || movement.operationId() == null || movement.hostileTokenId() == null) {
            throw new CorruptMovementFollowUpException(
                    "durable movement follow-up has no current hostile movement result");
        }
        UUID expectedCommandId = UUID.nameUUIDFromBytes(("movement-follow-up:" + movement.operationId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (!expectedCommandId.equals(followUp.commandId())) {
            throw new CorruptMovementFollowUpException(
                    "durable movement follow-up command id is not derived from movement operation");
        }
        if (!movement.operationId().equals(followUp.operationId())) {
            throw new CorruptMovementFollowUpException(
                    "durable movement follow-up operation id does not match movement result");
        }
        if (!movement.hostileTokenId().equals(followUp.hostileTokenId())) {
            throw new CorruptMovementFollowUpException(
                    "durable movement follow-up hostile token id does not match movement result");
        }
        if (followUp.kind() != MovementFollowUpCommand.Kind.COMBAT) {
            throw new CorruptMovementFollowUpException("durable movement follow-up kind is not COMBAT");
        }
        if (!"HOSTILE_OBSERVED".equals(followUp.trigger())) {
            throw new CorruptMovementFollowUpException(
                    "durable movement follow-up trigger is not HOSTILE_OBSERVED");
        }
        if (!turnId.equals(followUp.turnId())) {
            throw new CorruptMovementFollowUpException(
                    "durable movement follow-up turn id does not match movement command");
        }
    }

    private MovementFollowUpCommand readDurableFollowUp(RuntimeTurnCommand command) {
        return readStrictFollowUp(command.payloadJson(), command.commandId(), command.turnId());
    }

    private MovementFollowUpCommand readStrictFollowUp(String rawPayload, UUID commandId, UUID turnId) {
        try (JsonParser parser = objectMapper.createParser(rawPayload)) {
            MovementFollowUpCommand followUp = objectMapper.readerFor(MovementFollowUpCommand.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readValue(parser);
            if (parser.nextToken() != null) {
                throw new PermanentFollowUpFailure("invalid durable movement follow-up payload: trailing JSON");
            }
            if (!commandId.equals(followUp.commandId())) {
                throw new PermanentFollowUpFailure("durable movement follow-up command id does not match command");
            }
            if (!turnId.equals(followUp.turnId())) {
                throw new PermanentFollowUpFailure("durable movement follow-up turn id does not match command");
            }
            return followUp;
        } catch (PermanentFollowUpFailure failure) {
            throw failure;
        } catch (JsonProcessingException failure) {
            throw new PermanentFollowUpFailure(
                    "invalid durable movement follow-up payload: " + failure.getOriginalMessage());
        } catch (IOException failure) {
            throw new PermanentFollowUpFailure("invalid durable movement follow-up payload: " + failure.getMessage());
        } catch (IllegalArgumentException failure) {
            throw new PermanentFollowUpFailure("invalid durable movement follow-up payload: " + failure.getMessage());
        }
    }

    private RuntimeTurnCommand followUpCommand(RuntimeTurnCommand source, MovementFollowUpCommand followUp,
            String rawPayload, int order) {
        return RuntimeTurnCommand.create(source.turnId(), followUp.commandId(), source.adventureId(), source.sessionId(),
                source.ownerPlayerId(), source.targetContext(), "movement.follow-up", rawPayload, order);
    }

    private String rawFollowUpPayload(String rawMovement, MovementFollowUpCommand expected) {
        try (JsonParser parser = objectMapper.createParser(rawMovement)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new PermanentFollowUpFailure("durable movement outcome is invalid");
            }
            String rawFollowUp = null;
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new PermanentFollowUpFailure("durable movement outcome is invalid");
                }
                String fieldName = parser.currentName();
                JsonToken valueToken = parser.nextToken();
                if (valueToken == null) {
                    throw new PermanentFollowUpFailure("durable movement outcome is invalid");
                }
                if (!"followUp".equals(fieldName)) {
                    parser.skipChildren();
                    continue;
                }
                if (rawFollowUp != null) {
                    throw new PermanentFollowUpFailure("durable movement outcome follow-up is duplicated");
                }
                if (valueToken != JsonToken.START_OBJECT) {
                    throw new PermanentFollowUpFailure("durable movement outcome follow-up is invalid");
                }
                long start = parser.getTokenLocation().getCharOffset();
                parser.skipChildren();
                if (parser.currentToken() != JsonToken.END_OBJECT) {
                    throw new PermanentFollowUpFailure("durable movement outcome follow-up is invalid");
                }
                long end = parser.getTokenLocation().getCharOffset() + 1;
                if (start < 0 || end < start || end > rawMovement.length()) {
                    throw new PermanentFollowUpFailure("durable movement outcome follow-up boundary is invalid");
                }
                rawFollowUp = rawMovement.substring(Math.toIntExact(start), Math.toIntExact(end));
            }
            if (parser.nextToken() != null || rawFollowUp == null) {
                throw new PermanentFollowUpFailure("durable movement outcome follow-up is missing or trailing");
            }
            MovementFollowUpCommand parsed = readStrictFollowUp(rawFollowUp, expected.commandId(), expected.turnId());
            if (!parsed.equals(expected)) {
                throw new PermanentFollowUpFailure("durable movement follow-up payload mismatch");
            }
            return rawFollowUp;
        } catch (PermanentFollowUpFailure failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new PermanentFollowUpFailure("invalid durable movement follow-up payload: " + failure.getMessage());
        }
    }

    private Result repairRequired(UUID turnId, RuntimeTurn turn, RuntimeTurnCommand command,
            String message, CombatMapMoveResult movementResult) {
        RuntimeTurnCommand failed = command.failed(message);
        commandRepository.save(failed);
        RuntimeTurn repaired = turnRepository.findByTurnId(turnId).orElse(turn).markCommitRepairRequired();
        turnRepository.save(repaired);
        return new Result(Status.REPAIR_REQUIRED, repaired, failed, movementResult);
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
