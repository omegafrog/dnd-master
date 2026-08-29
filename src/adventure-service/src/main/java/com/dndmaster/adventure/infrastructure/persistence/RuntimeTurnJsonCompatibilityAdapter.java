package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import java.util.UUID;

/** Tolerant reader for rows written before the runtime lifecycle split. */
public final class RuntimeTurnJsonCompatibilityAdapter {
    private RuntimeTurnJsonCompatibilityAdapter() {
    }

    public static RuntimeTurn read(ObjectMapper mapper, String json, UUID persistedTurnId,
                                   UUID persistedCommandId, UUID persistedSessionId, UUID persistedScenarioPackageId) {
        Objects.requireNonNull(mapper, "object mapper must not be null");
        try {
            JsonNode parsed = mapper.readTree(Objects.requireNonNull(json, "runtime turn json must not be null"));
            if (parsed == null || !parsed.isObject()) {
                throw new RuntimeTurnCompatibilityException("runtime turn payload must be a JSON object");
            }
            ObjectNode payload = (ObjectNode) parsed;
            identity(payload, "turnId", persistedTurnId);
            identity(payload, "commandId", persistedCommandId);
            identity(payload, "sessionId", persistedSessionId);
            identity(payload, "scenarioPackageId", persistedScenarioPackageId);
            defaultLegacyPlanFields(payload);
            return mapper.treeToValue(payload, RuntimeTurn.class);
        } catch (RuntimeTurnCompatibilityException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeTurnCompatibilityException("runtime turn payload is incompatible", exception);
        }
    }

    private static void identity(ObjectNode payload, String field, UUID persistedValue) {
        Objects.requireNonNull(persistedValue, "persisted " + field + " must not be null");
        JsonNode value = payload.get(field);
        if (value == null || value.isNull()) {
            payload.put(field, persistedValue.toString());
            return;
        }
        try {
            if (!persistedValue.equals(UUID.fromString(value.asText()))) {
                throw new RuntimeTurnCompatibilityException("runtime turn " + field + " does not match its row identity");
            }
        } catch (IllegalArgumentException exception) {
            throw new RuntimeTurnCompatibilityException("runtime turn " + field + " is not a UUID", exception);
        }
    }

    private static void defaultLegacyPlanFields(ObjectNode payload) {
        JsonNode plan = payload.get("plan");
        if (!(plan instanceof ObjectNode planObject)) return;
        if (!planObject.hasNonNull("citedEvidence")) planObject.putArray("citedEvidence");
        if (!planObject.hasNonNull("warnings")) planObject.putArray("warnings");
        if (!planObject.hasNonNull("citationBindings")) planObject.putArray("citationBindings");
        if (!planObject.hasNonNull("attemptCount")) planObject.put("attemptCount", 1);
    }
}
