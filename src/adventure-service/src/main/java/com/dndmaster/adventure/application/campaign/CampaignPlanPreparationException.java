package com.dndmaster.adventure.application.campaign;

public final class CampaignPlanPreparationException extends RuntimeException {
    public enum Code {
        SESSION_NOT_FOUND,
        SESSION_ACCESS_DENIED,
        SESSION_NOT_PREPARABLE,
        ACTIVE_CHARACTER_SHEETS_REQUIRED,
        ACTIVE_CHARACTER_SHEET_UNAVAILABLE,
        STORYBOOK_SELECTION_REQUIRED,
        STORYBOOK_SELECTION_INVALID,
        STORYBOOK_NOT_READY,
        STORYBOOK_EVIDENCE_REQUIRED
    }

    private final Code code;

    public CampaignPlanPreparationException(Code code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code, "code must not be null");
    }

    public CampaignPlanPreparationException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = java.util.Objects.requireNonNull(code, "code must not be null");
    }

    public Code code() {
        return code;
    }
}
