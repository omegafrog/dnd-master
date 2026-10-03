package com.dndmaster.aigamemaster.application.endpoint;

public record ProviderConnectionState(String status, boolean cliAvailable, String operationId, String message) { }
