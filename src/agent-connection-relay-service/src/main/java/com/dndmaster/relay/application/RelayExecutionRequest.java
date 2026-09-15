package com.dndmaster.relay.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

public record RelayExecutionRequest(UUID soloPlayerId, String requestId, String operationId, String prompt,
                                    String model, String reasoning, String outputFormat, JsonNode outputSchema,
                                    List<String> imageInputs) {
    public RelayExecutionRequest {
        if (soloPlayerId == null) throw new IllegalArgumentException("soloPlayerId is required");
        requestId = required(requestId, "requestId");
        operationId = required(operationId, "operationId");
        prompt = required(prompt, "prompt");
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning");
        outputFormat = required(outputFormat, "outputFormat");
        imageInputs = imageInputs == null ? List.of() : List.copyOf(imageInputs);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
