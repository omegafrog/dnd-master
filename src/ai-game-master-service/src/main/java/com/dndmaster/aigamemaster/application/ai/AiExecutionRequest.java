package com.dndmaster.aigamemaster.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

/** Server-confirmed input for one completed AI execution request. */
public record AiExecutionRequest(UUID soloPlayerId, String requestId, String workId, String completedPrompt,
                                 String model, String reasoning, String outputFormat, JsonNode outputSchema,
                                 String imageDataUri) {
    public AiExecutionRequest {
        if (soloPlayerId == null) throw new IllegalArgumentException("solo player id is required");
        requestId = required(requestId, "request id");
        workId = required(workId, "work id");
        completedPrompt = required(completedPrompt, "completed prompt");
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning");
        outputFormat = required(outputFormat, "output format");
        imageDataUri = imageDataUri == null ? "" : imageDataUri.trim();
        if (!imageDataUri.isBlank() && !imageDataUri.startsWith("data:image/")) {
            throw new IllegalArgumentException("image input must be a data image URI");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
