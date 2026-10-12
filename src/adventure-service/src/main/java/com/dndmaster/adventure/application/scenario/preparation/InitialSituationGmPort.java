package com.dndmaster.adventure.application.scenario.preparation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.List;

/** Private structured GM request used only while preparing a new adventure. */
@FunctionalInterface
public interface InitialSituationGmPort {
    JsonNode generate(Request request);

    record Request(UUID ownerPlayerId, UUID sessionId, UUID endpointId, String provider, String model,
            String reasoning, String operationKey, String prompt, JsonNode outputSchema, boolean useSubagents,
            List<StageTask> stageTasks) {
        public Request(UUID ownerPlayerId, UUID sessionId, UUID endpointId, String provider, String model,
                String reasoning, String operationKey, String prompt, JsonNode outputSchema) {
            this(ownerPlayerId, sessionId, endpointId, provider, model, reasoning, operationKey, prompt, outputSchema, false, List.of());
        }
        public Request(UUID ownerPlayerId, UUID sessionId, UUID endpointId, String provider, String model,
                String reasoning, String operationKey, String prompt, JsonNode outputSchema, boolean useSubagents) {
            this(ownerPlayerId, sessionId, endpointId, provider, model, reasoning, operationKey, prompt, outputSchema, useSubagents, List.of());
        }
        public Request {
            stageTasks = stageTasks == null ? List.of() : List.copyOf(stageTasks);
            if (useSubagents && stageTasks.isEmpty()) throw new IllegalArgumentException("stage tasks are required for delegated preparation");
            if (stageTasks.stream().map(StageTask::stageId).distinct().count() != stageTasks.size())
                throw new IllegalArgumentException("stage task IDs must be unique");
            if (outputSchema == null || !outputSchema.isObject() || !"object".equals(outputSchema.path("type").asText())
                    || !outputSchema.path("properties").isObject() || !outputSchema.path("required").isArray()
                    || !outputSchema.path("additionalProperties").isBoolean()
                    || outputSchema.path("additionalProperties").booleanValue()) {
                throw new IllegalArgumentException("initial situation output schema must be a strict object schema");
            }
        }
    }

    record StageTask(String stageId, String prompt, JsonNode outputSchema) {
        public StageTask {
            if (stageId == null || stageId.isBlank() || prompt == null || prompt.isBlank()
                    || outputSchema == null || !outputSchema.isObject()) throw new IllegalArgumentException("stage task is incomplete");
            stageId = stageId.trim();
            outputSchema = outputSchema.deepCopy();
        }
        @Override public JsonNode outputSchema() { return outputSchema.deepCopy(); }
    }
}
