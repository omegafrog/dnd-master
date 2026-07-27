package com.dndmaster.ruleknowledge.application.registration;

public final class RulebookUploadConflictException extends RuntimeException {
    public RulebookUploadConflictException() {
        super("operation key was reused for a different rulebook upload");
    }
}
