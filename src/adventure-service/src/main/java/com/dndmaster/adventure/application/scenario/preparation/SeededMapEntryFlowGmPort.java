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
        if (!request.operationKey().contains(":stage:") || !request.openingSituationRequired()) return generated;

        String context = "operationKey=" + request.operationKey() + " expected=mapless_opening_map_required_destination actual=provider_response";
        long startedAt = com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.begin(
                LOGGER, "seed_map_entry_flow", context);
        int situationCount = -1;
        int openingIndex = -1;
        int destinationIndex = -1;
        long availableMapCount = 0;
        try {
            JsonNode situations = generated.path("situations");
            situationCount = situations.isArray() ? situations.size() : -1;
            openingIndex = 0;
            JsonNode mapIds = request.outputSchema().path("properties").path("situations").path("items")
                    .path("properties").path("mapDefinitionId").path("enum");
            String selectedMapId = null;
            for (JsonNode candidate : mapIds) {
                if (candidate.isTextual()) {
                    selectedMapId = candidate.asText();
                    break;
                }
            }
            availableMapCount = countTextValues(mapIds);
            destinationIndex = request.requiredSituationCount();
            if (!situations.isArray() || openingIndex < 0 || openingIndex >= situations.size()
                    || destinationIndex <= openingIndex || destinationIndex >= situations.size() || selectedMapId == null) {
                throw new IllegalStateException("map entry fixture requires an opening, destination, and available map definition");
            }

            JsonNode response = generated.deepCopy();
            ArrayNode seededSituations = (ArrayNode) response.path("situations");
            for (int index = 0; index < seededSituations.size(); index++) {
                ObjectNode situation = (ObjectNode) seededSituations.get(index);
                if (index == openingIndex) {
                    situation.put("mapNeeded", false);
                    situation.putNull("mapDefinitionId");
                } else if (index == destinationIndex) {
                    situation.put("mapNeeded", true);
                    situation.put("mapDefinitionId", selectedMapId);
                } else {
                    situation.put("mapNeeded", false);
                    situation.putNull("mapDefinitionId");
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
                            + " expectedSituationCountAtLeast=2 actualSituationCount=" + situationCount
                            + " expectedOpeningIndexInRange=true actualOpeningIndex=" + openingIndex
                            + " expectedDestinationIndex=nonOpening actualDestinationIndex=" + destinationIndex
                            + " expectedAvailableMapCountAtLeast=1 actualAvailableMapCount=" + availableMapCount
                            + " failure=invalid_provider_response", startedAt, failure);
            throw failure;
        }
    }

    private static long countTextValues(JsonNode values) {
        long count = 0;
        for (JsonNode value : values) if (value.isTextual()) count++;
        return count;
    }

}
