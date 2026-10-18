package com.dndmaster.adventure.application.scenario.preparation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in E2E fixture that pins a mapless opening and one map-required next situation. */
public final class SeededMapEntryFlowGmPort implements InitialSituationGmPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeededMapEntryFlowGmPort.class);
    private final InitialSituationGmPort delegate;

    public SeededMapEntryFlowGmPort(InitialSituationGmPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public JsonNode generate(Request request) {
        JsonNode generated = delegate.generate(request);
        if (!request.operationKey().contains(":stage:") || request.operationKey().contains(":index-repair")
                || !openingRequired(request.outputSchema())) return generated;

        String context = "operationKey=" + request.operationKey() + " expected=mapless_opening_map_required_destination actual=provider_response";
        long startedAt = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(
                LOGGER, "seed_map_entry_flow", context);
        try {
            JsonNode situations = generated.path("situations");
            int openingIndex = generated.path("openingSituationIndex").asInt(-1);
            JsonNode mapIds = request.outputSchema().path("properties").path("situations").path("items")
                    .path("properties").path("mapDefinitionId").path("enum");
            String selectedMapId = null;
            for (JsonNode candidate : mapIds) {
                if (candidate.isTextual()) {
                    selectedMapId = candidate.asText();
                    break;
                }
            }
            int destinationIndex = firstNonOpeningIndex(situations, openingIndex);
            if (!situations.isArray() || openingIndex < 0 || openingIndex >= situations.size()
                    || destinationIndex < 0 || selectedMapId == null) {
                throw new IllegalStateException("map entry fixture requires an opening, destination, and available map definition");
            }

            JsonNode response = generated.deepCopy();
            ArrayNode seededSituations = (ArrayNode) response.path("situations");
            String openingId = seededSituations.get(openingIndex).path("situationId").asText();
            String destinationId = seededSituations.get(destinationIndex).path("situationId").asText();
            if (openingId.isBlank() || destinationId.isBlank()) {
                throw new IllegalStateException("map entry fixture requires situation identifiers");
            }
            for (int index = 0; index < seededSituations.size(); index++) {
                ObjectNode situation = (ObjectNode) seededSituations.get(index);
                if (index == openingIndex) {
                    situation.put("mapNeeded", false);
                    situation.putNull("mapDefinitionId");
                } else if (index == destinationIndex) {
                    situation.put("mapNeeded", true);
                    situation.put("mapDefinitionId", selectedMapId);
                    situation.putArray("prerequisiteSituationIds").add(openingId);
                } else {
                    situation.put("mapNeeded", false);
                    situation.putNull("mapDefinitionId");
                    situation.putArray("prerequisiteSituationIds").add(destinationId);
                }
            }
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "seed_map_entry_flow", "operationKey=" + request.operationKey()
                            + " expected=opening_mapless_destination_map_required actualMapDefinitionId=" + selectedMapId
                            + " openingIndex=" + openingIndex + " destinationIndex=" + destinationIndex
                            + " situationCount=" + seededSituations.size() + " mapCount=" + countTextValues(mapIds), startedAt);
            return response;
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "seed_map_entry_flow", "operationKey=" + request.operationKey()
                            + " expected=opening_and_destination actual=invalid_provider_response", startedAt, failure);
            throw failure;
        }
    }

    private static int firstNonOpeningIndex(JsonNode situations, int openingIndex) {
        if (!situations.isArray()) return -1;
        for (int index = 0; index < situations.size(); index++) {
            if (index != openingIndex) return index;
        }
        return -1;
    }

    private static long countTextValues(JsonNode values) {
        long count = 0;
        for (JsonNode value : values) if (value.isTextual()) count++;
        return count;
    }

    private static boolean openingRequired(JsonNode schema) {
        JsonNode type = schema.path("properties").path("openingSituationIndex").path("type");
        if (type.isTextual()) return "integer".equals(type.asText());
        if (!type.isArray()) return false;
        for (JsonNode value : type) if ("null".equals(value.asText())) return false;
        return true;
    }
}
