package com.dndmaster.relay.application;

public record ConnectionControlCommand(String requestId, String action, String operationId,
                                       String operationType) {
    public ConnectionControlCommand {
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId is required");
        if (action == null || !java.util.Set.of("STATUS", "START", "POLL", "DISCONNECT").contains(action))
            throw new IllegalArgumentException("unsupported connection action");
        operationId = operationId == null ? "" : operationId.trim();
        operationType = operationType == null ? "" : operationType.trim();
        if ("START".equals(action) && !java.util.Set.of("CONNECT", "REAUTHENTICATE", "SWITCH_ACCOUNT").contains(operationType))
            throw new IllegalArgumentException("unsupported connection operation type");
        if (("POLL".equals(action)) && operationId.isBlank()) throw new IllegalArgumentException("operationId is required");
    }
}
