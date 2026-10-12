package com.dndmaster.aigamemaster.application.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;
import java.util.List;

/** Server-confirmed input for one completed AI execution request. */
public record AiExecutionRequest(UUID soloPlayerId, String requestId, String workId, String completedPrompt,
                                 String model, String reasoning, String outputFormat, JsonNode outputSchema,
                                 String imageDataUri, JsonNode ragSearchContext, List<Subtask> subtasks) {
    public AiExecutionRequest(UUID soloPlayerId, String requestId, String workId, String completedPrompt,
                              String model, String reasoning, String outputFormat, JsonNode outputSchema,
                              String imageDataUri) {
        this(soloPlayerId, requestId, workId, completedPrompt, model, reasoning, outputFormat, outputSchema,
                imageDataUri, null, List.of());
    }
    public AiExecutionRequest(UUID soloPlayerId, String requestId, String workId, String completedPrompt,
                              String model, String reasoning, String outputFormat, JsonNode outputSchema,
                              String imageDataUri, JsonNode ragSearchContext) {
        this(soloPlayerId, requestId, workId, completedPrompt, model, reasoning, outputFormat, outputSchema,
                imageDataUri, ragSearchContext, List.of());
    }
    public AiExecutionRequest {
        if (soloPlayerId == null) throw new IllegalArgumentException("solo player id is required");
        requestId = required(requestId, "request id");
        workId = required(workId, "work id");
        completedPrompt = required(completedPrompt, "completed prompt");
        model = required(model, "model");
        reasoning = required(reasoning, "reasoning");
        outputFormat = required(outputFormat, "output format");
        imageDataUri = imageDataUri == null ? "" : imageDataUri.trim();
        subtasks = subtasks == null ? List.of() : List.copyOf(subtasks);
        if (!subtasks.isEmpty() && outputSchema == null) throw new IllegalArgumentException("parent output schema is required when subtasks are present");
        if (subtasks.stream().map(Subtask::stageId).distinct().count() != subtasks.size()) throw new IllegalArgumentException("subtask stageIds must be unique");
        if (!imageDataUri.isBlank() && !imageDataUri.startsWith("data:image/")) {
            throw new IllegalArgumentException("image input must be a data image URI");
        }
    }

    public record Subtask(String stageId, String prompt, JsonNode outputSchema) {
        public Subtask {
            stageId = required(stageId, "subtask stageId");
            prompt = required(prompt, "subtask prompt");
            if (outputSchema == null || !outputSchema.isObject()) throw new IllegalArgumentException("subtask output schema must be an object");
            outputSchema = outputSchema.deepCopy();
        }
        @Override public JsonNode outputSchema() { return outputSchema.deepCopy(); }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
