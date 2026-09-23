package com.dndmaster.aigamemaster.application.evidence;

import java.util.UUID;

/** A single provider attempt. Retry ownership stays with Adventure evidence orchestration. */
@FunctionalInterface
public interface EvidenceModelPort {
    String complete(UUID soloPlayerId, String operationId, String instruction);
}
