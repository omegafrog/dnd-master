package com.dndmaster.ruleknowledge.application.definition;

public record RulebookActionEvidence(String id, String locator, String text) {
    public RulebookActionEvidence {
        if (id == null || id.isBlank() || locator == null || locator.isBlank() || text == null || text.isBlank())
            throw new IllegalArgumentException("evidence identity, locator, and text are required");
    }
}
