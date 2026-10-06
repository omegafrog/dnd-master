package com.dndmaster.relay.application;

public record AgentConnectionControlResult(String messageType, String requestId, String status,
                                            boolean cliAvailable, String operationId,
                                            String operationStatus, boolean pending,
                                            String authUrl, String message) {
    public static AgentConnectionControlResult failure(String requestId, String status, String message) {
        return new AgentConnectionControlResult("CONNECTION_CONTROL_RESULT", requestId, status,
                false, null, null, false, null, message);
    }
}
