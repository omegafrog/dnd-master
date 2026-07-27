package com.dndmaster.ruleknowledge.domain.rulebook;

import java.util.Objects;

public enum DocumentType {
    RULEBOOK,
    STORYBOOK;

    public static DocumentType require(DocumentType value) {
        return Objects.requireNonNull(value, "documentType must not be null");
    }
}
