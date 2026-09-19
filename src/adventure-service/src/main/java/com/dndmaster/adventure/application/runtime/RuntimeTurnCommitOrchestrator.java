package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.combat.CombatMapMoveResult;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
                    RuntimeTurnCommand followUp = followUpCommand(command, movementResult.followUp(),
                            rawFollowUpPayload(command.outcomeJson(), movementResult.followUp()), nextExecutionOrder++);
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
                    RuntimeTurnCommand followUp = followUpCommand(command, movementResult.followUp(),
                            rawFollowUpPayload(execution.value(), movementResult.followUp()), nextExecutionOrder++);
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
            MovementFollowUpCommand followUp = readDurableFollowUp(command.payloadJson());
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
        } catch (RuntimeException failure) {
            return RuntimeTurnCommandExecution.transientFailure(failure.getMessage());
        }
    }

    private MovementFollowUpCommand readDurableFollowUp(String payload) {
        try (JsonParser parser = objectMapper.createParser(payload)) {
            MovementFollowUpCommand followUp = objectMapper.readerFor(MovementFollowUpCommand.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readValue(parser);
            if (parser.nextToken() != null) {
                throw new PermanentFollowUpFailure("invalid durable movement follow-up payload: trailing JSON");
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
        try {
            JsonNode movement = readStrictObject(rawMovement, "durable movement outcome");
            JsonNode rawFollowUp = movement.get("followUp");
            if (rawFollowUp == null || !rawFollowUp.isObject()) {
                throw new PermanentFollowUpFailure("durable movement outcome follow-up is missing");
            }
            String raw = extractRawObjectField(rawMovement, "followUp");
            JsonNode parsed = readStrictObject(raw, "durable movement follow-up");
            if (!raw.equals(objectMapper.writeValueAsString(parsed))) {
                throw new PermanentFollowUpFailure("durable movement follow-up payload is not canonical");
            }
            if (!parsed.equals(objectMapper.valueToTree(expected))) {
                throw new PermanentFollowUpFailure("durable movement follow-up payload mismatch");
            }
            return raw;
        } catch (PermanentFollowUpFailure failure) {
            return "{not-json";
        } catch (IOException | RuntimeException failure) {
            return "{not-json";
        }
    }

    private JsonNode readStrictObject(String raw, String description) throws IOException {
        try (JsonParser parser = objectMapper.createParser(raw)) {
            JsonNode value = objectMapper.readTree(parser);
            if (value == null || !value.isObject() || parser.nextToken() != null) {
                throw new PermanentFollowUpFailure(description + " is invalid");
            }
            return value;
        }
    }

    /** Extracts the original JSON value without normalizing whitespace or field order. */
    private String extractRawObjectField(String json, String field) {
        String needle = "\"" + field + "\"";
        int key = json.indexOf(needle);
        if (key < 0) throw new PermanentFollowUpFailure("durable movement follow-up is missing");
        int colon = json.indexOf(':', key + needle.length());
        if (colon < 0) throw new PermanentFollowUpFailure("durable movement follow-up is invalid");
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '{') {
            throw new PermanentFollowUpFailure("durable movement follow-up is invalid");
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(start, i + 1);
        }
        throw new PermanentFollowUpFailure("durable movement follow-up is invalid");
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
