package com.dndmaster.ruleknowledge.application.definition;

public record RulebookActionDefinitionCoverage(String id, String name, String sourceLocator, String status, String reason) {
    public RulebookActionDefinitionCoverage {
        if (id == null || id.isBlank() || name == null || name.isBlank() || sourceLocator == null || sourceLocator.isBlank()
                || !java.util.Set.of("COMPLETED", "INSUFFICIENT_EVIDENCE", "FAILED").contains(status))
            throw new IllegalArgumentException("action definition coverage is invalid");
        if (reason != null && reason.length() > 200) throw new IllegalArgumentException("coverage reason is too long");
    }
}
