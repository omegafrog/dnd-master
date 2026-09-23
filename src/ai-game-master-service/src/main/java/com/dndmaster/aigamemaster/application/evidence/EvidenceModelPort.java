package com.dndmaster.aigamemaster.application.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** A single provider attempt. Retry ownership stays with Adventure evidence orchestration. */
@FunctionalInterface
public interface EvidenceModelPort {
    String complete(String operationId, String instruction);

    default String complete(UUID soloPlayerId, String operationId, String instruction) {
        return complete(operationId, instruction);
    }

    /** Requests the same completion with a provider-enforced JSON output shape when available. */
    default String complete(UUID soloPlayerId, String operationId, String instruction, JsonNode outputSchema) {
        return complete(soloPlayerId, operationId, instruction);
    }
}
