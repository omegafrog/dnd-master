package com.dndmaster.adventure.application.runtime;

public final class RuntimeEvidenceSelectionException extends IllegalStateException {
    private final RuntimeEvidenceSelectionViolation violation;

    public RuntimeEvidenceSelectionException(RuntimeEvidenceSelectionViolation violation) {
        super(violation.code() + ": " + violation.safeMessage());
        this.violation = violation;
    }

    public RuntimeEvidenceSelectionViolation violation() {
        return violation;
    }
}
