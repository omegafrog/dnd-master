package com.dndmaster.aigamemaster.application.evidence;

/** A single provider attempt. Retry ownership stays with Adventure evidence orchestration. */
@FunctionalInterface
public interface EvidenceModelPort {
    String complete(String operationId, String instruction);
}
