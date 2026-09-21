package com.dndmaster.aigamemaster.application.evidence;

/** The provider response violates the evidence-model output contract. */
public final class EvidenceModelOutputException extends RuntimeException {
    public EvidenceModelOutputException(String message) { super(message); }
    public EvidenceModelOutputException(String message, Throwable cause) { super(message, cause); }
}
