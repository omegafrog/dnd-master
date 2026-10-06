package com.dndmaster.aigamemaster.localcodex;

public record ConnectionOperationResult(String operationId, ProviderConnectionStatus status, String authUrl,
                                        String message) {
    public boolean pending() { return status == ProviderConnectionStatus.AUTHENTICATING; }
}
