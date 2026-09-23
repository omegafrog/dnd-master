package com.dndmaster.aigamemaster.application.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import java.util.function.Function;

final class EvidenceModelStageExecutor {
    private final EvidenceModelPort model;

    EvidenceModelStageExecutor(EvidenceModelPort model) { this.model = model; }

    <T> T execute(String operationId, String instruction, Function<String, T> parser) {
        return parser.apply(model.complete(operationId, instruction));
    }

    <T> T execute(UUID soloPlayerId, String operationId, String instruction, Function<String, T> parser) {
        return parser.apply(soloPlayerId == null
                ? model.complete(operationId, instruction)
                : model.complete(soloPlayerId, operationId, instruction));
    }

    <T> T execute(UUID soloPlayerId, String operationId, String instruction, JsonNode outputSchema,
                  Function<String, T> parser) {
        return parser.apply(soloPlayerId == null
                ? model.complete(operationId, instruction)
                : model.complete(soloPlayerId, operationId, instruction, outputSchema));
    }
}
