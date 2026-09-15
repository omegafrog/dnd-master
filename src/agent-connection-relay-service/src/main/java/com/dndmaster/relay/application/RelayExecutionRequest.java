package com.dndmaster.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt,
                                    String model, String reasoning, String outputFormat, JsonNode outputSchema,
                                    List<String> imageInputs, long deadlineEpochMillis, String connectionId) {
    public RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                                 String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs) {
        this(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, 0, "");
    }
    public RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                                 String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs, long deadlineEpochMillis) {
        this(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, deadlineEpochMillis, "");
    }
    public RelayExecutionRequest {
        if (soloPlayerId == null) throw new IllegalArgumentException("soloPlayerId is required");
        requestId = required(requestId, "requestId");
        operationId = required(operationId, "operationId");
        prompt = requiredPreserving(prompt, "prompt");
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning");
        outputFormat = required(outputFormat, "outputFormat");
        imageInputs = imageInputs == null ? List.of() : List.copyOf(imageInputs);
        if (deadlineEpochMillis < 0) throw new IllegalArgumentException("deadlineEpochMillis cannot be negative");
        connectionId = connectionId == null ? "" : connectionId.trim();
    }
    public RelayExecutionRequest withDeadline(long deadline) {
        return new RelayExecutionRequest(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, deadline, connectionId);
    }
    public RelayExecutionRequest withConnectionId(String connectionId) {
        return new RelayExecutionRequest(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, deadlineEpochMillis, connectionId);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
    private static String requiredPreserving(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }
}
