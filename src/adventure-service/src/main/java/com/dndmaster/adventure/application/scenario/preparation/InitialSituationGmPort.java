package com.dndmaster.adventure.application.scenario.preparation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Private structured GM request used only while preparing a new adventure. */
@FunctionalInterface
public interface InitialSituationGmPort {
    JsonNode generate(Request request);

    record Request(UUID ownerPlayerId, UUID sessionId, UUID endpointId, String provider, String model,
            String reasoning, String operationKey, String prompt) {}
}
