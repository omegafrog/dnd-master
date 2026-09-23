package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.application.evidence.EvidenceModelPort;
import java.util.Objects;

/** Adapts the configured AI completion provider to the evidence-model boundary. */
public final class GmEvidenceModelAdapter implements EvidenceModelPort {
    private final GmCompletionAdapter completionAdapter;

    public GmEvidenceModelAdapter(GmCompletionAdapter completionAdapter) {
        this.completionAdapter = Objects.requireNonNull(completionAdapter, "completionAdapter must not be null");
    }

    @Override
    public String complete(String operationId, String instruction) {
        return completionAdapter.complete(operationId, instruction, value -> value);
    }

    @Override
    public String complete(java.util.UUID soloPlayerId, String operationId, String instruction) {
        return completionAdapter.complete(soloPlayerId, operationId, instruction, value -> value);
    }

    @Override
    public String complete(java.util.UUID soloPlayerId, String operationId, String instruction,
                           com.fasterxml.jackson.databind.JsonNode outputSchema) {
        return completionAdapter.complete(soloPlayerId, operationId, new GmPrompt(instruction), value -> value, outputSchema);
    }
}
