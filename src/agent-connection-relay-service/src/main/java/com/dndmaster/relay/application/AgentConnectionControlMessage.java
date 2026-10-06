package com.dndmaster.relay.application;

public record AgentConnectionControlMessage(String messageType, String requestId, String action,
                                             String operationId, String operationType) {
    public AgentConnectionControlMessage(String requestId, String action, String operationId, String operationType) {
        this("CONNECTION_CONTROL", requestId, action, operationId, operationType);
    }
}
