package com.dndmaster.aigamemaster.localcodex;

public record AgentConnectionControlResponse(String messageType, String requestId, String status,
                                              boolean cliAvailable, String operationId,
                                              String operationStatus, boolean pending,
                                              String authUrl, String message) {
    public static AgentConnectionControlResponse from(String requestId, ConnectionStatus status) {
        return new AgentConnectionControlResponse("CONNECTION_CONTROL_RESULT", requestId,
                status.status().name(), status.cliAvailable(), status.operationId(), null,
                status.status() == ProviderConnectionStatus.AUTHENTICATING, null, null);
    }
    public static AgentConnectionControlResponse from(String requestId, ConnectionOperationResult result) {
        return new AgentConnectionControlResponse("CONNECTION_CONTROL_RESULT", requestId,
                result.status().name(), result.status() != ProviderConnectionStatus.CLI_UNAVAILABLE,
                result.operationId(), result.status().name(),
                result.pending(), result.authUrl(), result.message());
    }
}
