package com.dndmaster.adventure.application.scenario.preparation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Private structured GM request used only while preparing a new adventure. */
@FunctionalInterface
public interface InitialSituationGmPort {
    JsonNode generate(Request request);

    record Request(UUID ownerPlayerId, UUID sessionId, UUID endpointId, String provider, String model,
            String reasoning, String operationKey, String prompt, JsonNode outputSchema) {
        public Request {
            if (outputSchema == null || !outputSchema.isObject() || !"object".equals(outputSchema.path("type").asText())
                    || !outputSchema.path("properties").isObject() || !outputSchema.path("required").isArray()
                    || !outputSchema.path("additionalProperties").isBoolean()
                    || outputSchema.path("additionalProperties").booleanValue()) {
                throw new IllegalArgumentException("initial situation output schema must be a strict object schema");
            }
        }
    }
}
