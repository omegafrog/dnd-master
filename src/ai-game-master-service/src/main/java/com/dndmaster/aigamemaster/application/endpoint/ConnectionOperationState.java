package com.dndmaster.aigamemaster.application.endpoint;

public record ConnectionOperationState(String operationId, String status, String authUrl,
                                       String message, boolean pending) { }
