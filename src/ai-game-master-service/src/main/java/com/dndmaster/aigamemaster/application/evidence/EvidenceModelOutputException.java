package com.dndmaster.aigamemaster.application.evidence;

/** The provider response violates the evidence-model output contract. */
public final class EvidenceModelOutputException extends RuntimeException {
    public enum Category { INVALID_SCHEMA, INVALID_IDENTIFIER, INVALID_VALUE, MALFORMED_JSON, INVALID_MODEL_OUTPUT }

    private final Category category;

    public EvidenceModelOutputException(String message) {
        this(Category.INVALID_MODEL_OUTPUT, message, null);
    }

    public EvidenceModelOutputException(String message, Throwable cause) {
        this(Category.INVALID_MODEL_OUTPUT, message, cause);
    }

    public EvidenceModelOutputException(Category category, String message) {
        this(category, message, null);
    }

    public EvidenceModelOutputException(Category category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public Category category() { return category; }
}
