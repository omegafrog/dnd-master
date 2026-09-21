package com.dndmaster.aigamemaster.application.evidence;

import java.util.function.Function;

final class EvidenceModelStageExecutor {
    private final EvidenceModelPort model;

    EvidenceModelStageExecutor(EvidenceModelPort model) { this.model = model; }

    <T> T execute(String operationId, String instruction, Function<String, T> parser) {
        RuntimeException failure = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return parser.apply(model.complete(operationId + ":" + (attempt + 1), instruction));
            } catch (RuntimeException caught) {
                failure = caught;
            }
        }
        throw failure;
    }
}
