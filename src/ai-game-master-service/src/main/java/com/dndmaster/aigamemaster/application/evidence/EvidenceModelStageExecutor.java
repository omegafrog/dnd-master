package com.dndmaster.aigamemaster.application.evidence;

import java.util.function.Function;

final class EvidenceModelStageExecutor {
    private final EvidenceModelPort model;

    EvidenceModelStageExecutor(EvidenceModelPort model) { this.model = model; }

    <T> T execute(String operationId, String instruction, Function<String, T> parser) {
        return parser.apply(model.complete(operationId, instruction));
    }
}
