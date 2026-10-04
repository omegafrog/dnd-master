package com.dndmaster.aigamemaster.localcodex;

public record ConnectionStatus(ProviderConnectionStatus status, boolean cliAvailable, String operationId) { }
