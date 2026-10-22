package com.dndmaster.adventure.application.scenario.preparation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Test-run fixture that pins the opening situation to an available map after normal preparation. */
public final class SeededOpeningSituationMapGmPort implements InitialSituationGmPort {
    private static final Logger LOGGER = LoggerFactory.getLogger(SeededOpeningSituationMapGmPort.class);
    private final InitialSituationGmPort delegate;

    public SeededOpeningSituationMapGmPort(InitialSituationGmPort delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public JsonNode generate(Request request) {
        JsonNode generated = delegate.generate(request);
        if (!request.operationKey().contains(":stage:") || !request.openingSituationRequired()) return generated;

        String context = "operationKey=" + request.operationKey() + " expected=opening_map_required actual=provider_response";
        long startedAt = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(
                LOGGER, "seed_opening_situation_map", context);
        try {
            JsonNode situations = generated.path("situations");
            int openingIndex = 0;
            JsonNode mapIds = request.outputSchema().path("properties").path("situations").path("items")
                    .path("properties").path("mapDefinitionId").path("enum");
            String selectedMapId = null;
            for (JsonNode candidate : mapIds) {
                if (candidate.isTextual()) {
                    selectedMapId = candidate.asText();
                    break;
                }
            }
            if (!situations.isArray() || openingIndex < 0 || openingIndex >= situations.size() || selectedMapId == null) {
                throw new IllegalStateException("opening map fixture requires a valid opening situation and available map definition");
            }
            JsonNode response = generated.deepCopy();
            ObjectNode opening = (ObjectNode) response.path("situations").get(openingIndex);
            opening.put("mapNeeded", true);
            opening.put("mapDefinitionId", selectedMapId);
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.complete(LOGGER,
                    "seed_opening_situation_map", "operationKey=" + request.operationKey()
                            + " expected=opening_map_required actualMapDefinitionId=" + selectedMapId
                            + " openingIndex=" + openingIndex + " mapCount=" + countTextValues(mapIds), startedAt);
            return response;
        } catch (RuntimeException | Error failure) {
            com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.fail(LOGGER,
                    "seed_opening_situation_map", "operationKey=" + request.operationKey()
                            + " expected=opening_situation_and_map_id actual=invalid_provider_response", startedAt, failure);
            throw failure;
        }
    }

    private static long countTextValues(JsonNode values) {
        long count = 0;
        for (JsonNode value : values) if (value.isTextual()) count++;
        return count;
    }

}
