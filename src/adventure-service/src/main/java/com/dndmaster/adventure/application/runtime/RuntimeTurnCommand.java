package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.UUID;

/** A durable, ordered mutation belonging to one RuntimeTurn saga. */
public record RuntimeTurnCommand(
        UUID commandId,
        UUID turnId,
        UUID adventureId,
        UUID sessionId,
        UUID ownerPlayerId,
        String targetContext,
        String commandType,
        String payloadJson,
        ExecutionStatus executionStatus,
        int executionOrder,
        String idempotencyKey,
        String lastError,
        int attemptCount,
        String outcomeJson) {

    public enum ExecutionStatus { PENDING, DONE, FAILED }

    public RuntimeTurnCommand {
        commandId = Objects.requireNonNull(commandId, "command id must not be null");
        turnId = Objects.requireNonNull(turnId, "turn id must not be null");
        adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        sessionId = Objects.requireNonNull(sessionId, "session id must not be null");
        ownerPlayerId = Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        targetContext = required(targetContext, "target context");
        commandType = required(commandType, "command type");
        payloadJson = Objects.requireNonNull(payloadJson, "payload json must not be null");
        executionStatus = Objects.requireNonNull(executionStatus, "execution status must not be null");
        if (executionOrder < 0) throw new IllegalArgumentException("execution order must not be negative");
        idempotencyKey = required(idempotencyKey, "idempotency key");
        if (attemptCount < 0) throw new IllegalArgumentException("attempt count must not be negative");
        lastError = lastError == null ? "" : lastError.trim();
        outcomeJson = outcomeJson == null ? "" : outcomeJson;
    }

    public static RuntimeTurnCommand create(UUID turnId, UUID commandId, UUID adventureId, UUID sessionId,
            UUID ownerPlayerId, String targetContext, String commandType, String payloadJson, int executionOrder) {
        return new RuntimeTurnCommand(commandId, turnId, adventureId, sessionId, ownerPlayerId, targetContext,
                commandType, payloadJson, ExecutionStatus.PENDING, executionOrder,
                turnId + ":" + commandId, "", 0, "");
    }

    public RuntimeTurnCommand withStatus(ExecutionStatus status) {
        return new RuntimeTurnCommand(commandId, turnId, adventureId, sessionId, ownerPlayerId, targetContext,
                commandType, payloadJson, status, executionOrder, idempotencyKey, lastError, attemptCount, outcomeJson);
    }

    public RuntimeTurnCommand withExecutionOrder(int order) {
        return new RuntimeTurnCommand(commandId, turnId, adventureId, sessionId, ownerPlayerId, targetContext,
                commandType, payloadJson, executionStatus, order, idempotencyKey, lastError, attemptCount, outcomeJson);
    }

    public RuntimeTurnCommand done(String outcome) {
        return new RuntimeTurnCommand(commandId, turnId, adventureId, sessionId, ownerPlayerId, targetContext,
                commandType, payloadJson, ExecutionStatus.DONE, executionOrder, idempotencyKey, "", attemptCount + 1,
                outcome == null ? "" : outcome);
    }

    public RuntimeTurnCommand failed(String error) {
        return new RuntimeTurnCommand(commandId, turnId, adventureId, sessionId, ownerPlayerId, targetContext,
                commandType, payloadJson, ExecutionStatus.FAILED, executionOrder, idempotencyKey,
                required(error == null || error.isBlank() ? "unknown command failure" : error, "command error"),
                attemptCount + 1, outcomeJson);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
