package com.dndmaster.relay.application;

import java.util.UUID;

public record ConnectionControlRequest(UUID soloPlayerId, String requestId, String action,
                                       String operationId, String operationType,
                                       long deadlineEpochMillis, String connectionId) {
    public ConnectionControlRequest {
        if (soloPlayerId == null) throw new IllegalArgumentException("soloPlayerId is required");
        requestId = required(requestId, "requestId");
        action = required(action, "action");
        operationId = operationId == null ? "" : operationId.trim();
        operationType = operationType == null ? "" : operationType.trim();
        if (deadlineEpochMillis < 0) throw new IllegalArgumentException("deadlineEpochMillis cannot be negative");
        connectionId = connectionId == null ? "" : connectionId.trim();
    }
    public ConnectionControlRequest withDeadline(long deadline) {
        return new ConnectionControlRequest(soloPlayerId, requestId, action, operationId,
                operationType, deadline, connectionId);
    }
    public ConnectionControlRequest withConnectionId(String value) {
        return new ConnectionControlRequest(soloPlayerId, requestId, action, operationId,
                operationType, deadlineEpochMillis, value);
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
