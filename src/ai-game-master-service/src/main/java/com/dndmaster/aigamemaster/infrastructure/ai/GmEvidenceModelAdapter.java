package com.dndmaster.aigamemaster.infrastructure.ai;

import com.dndmaster.aigamemaster.application.evidence.EvidenceModelPort;
import java.util.Objects;
import java.util.UUID;

/** Routes evidence-model requests through the configured game-master execution path. */
public final class GmEvidenceModelAdapter implements EvidenceModelPort {
    private final GmCompletionAdapter completionAdapter;

    public GmEvidenceModelAdapter(GmCompletionAdapter completionAdapter) {
        this.completionAdapter = Objects.requireNonNull(completionAdapter, "completionAdapter must not be null");
    }

    @Override
    public String complete(UUID soloPlayerId, String operationId, String instruction) {
        if (soloPlayerId == null) throw new IllegalArgumentException("evidence-model request requires the server-confirmed Solo Player ID");
        return completionAdapter.complete(soloPlayerId, operationId, instruction, value -> value);
    }
}
