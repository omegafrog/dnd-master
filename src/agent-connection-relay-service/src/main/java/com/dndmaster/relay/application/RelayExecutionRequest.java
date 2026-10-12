package com.dndmaster.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt,
                                    String model, String reasoning, String outputFormat, JsonNode outputSchema,
                                    List<String> imageInputs, long deadlineEpochMillis, String connectionId,
                                    JsonNode ragSearchContext, List<RelaySubtask> subtasks) {
    public RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                                 String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs) {
        this(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, 0, "", null, List.of());
    }
    public RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                                 String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs, long deadlineEpochMillis) {
        this(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, deadlineEpochMillis, "", null, List.of());
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
        subtasks = subtasks == null ? List.of() : List.copyOf(subtasks);
        if (deadlineEpochMillis < 0) throw new IllegalArgumentException("deadlineEpochMillis cannot be negative");
        connectionId = connectionId == null ? "" : connectionId.trim();
    }
    public RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt, String model,
                                 String reasoning, String outputFormat, JsonNode outputSchema, List<String> imageInputs,
                                 long deadlineEpochMillis, String connectionId) {
        this(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs,
                deadlineEpochMillis, connectionId, null, List.of());
    }
    public RelayExecutionRequest withDeadline(long deadline) {
        return new RelayExecutionRequest(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, deadline, connectionId, ragSearchContext, subtasks);
    }
    public RelayExecutionRequest withConnectionId(String connectionId) {
        return new RelayExecutionRequest(soloPlayerId, requestId, operationId, prompt, model, reasoning, outputFormat, outputSchema, imageInputs, deadlineEpochMillis, connectionId, ragSearchContext, subtasks);
    }

    public record RelaySubtask(String stageId, String prompt, JsonNode outputSchema) {
        public RelaySubtask {
            if (stageId == null || stageId.isBlank() || prompt == null || prompt.isBlank()
                    || outputSchema == null || !outputSchema.isObject()) throw new IllegalArgumentException("subtask is incomplete");
            outputSchema = outputSchema.deepCopy();
        }
        @Override public JsonNode outputSchema() { return outputSchema.deepCopy(); }
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
