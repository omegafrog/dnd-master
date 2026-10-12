package com.dndmaster.ruleknowledge.application.definition;

public record RulebookActionDefinitionResult(String candidateId, String status,
        RulebookActionDefinition definition, String reason, java.util.List<String> neededFields,
        java.util.List<RulebookActionSharedRule> sharedRules, java.util.List<String> sharedRuleRefs) {
    public RulebookActionDefinitionResult(String candidateId, String status, RulebookActionDefinition definition, String reason) {
        this(candidateId, status, definition, reason, java.util.List.of(), java.util.List.of(), java.util.List.of());
    }
    public RulebookActionDefinitionResult(String candidateId, String status, RulebookActionDefinition definition, String reason,
            java.util.List<String> neededFields) {
        this(candidateId, status, definition, reason, neededFields, java.util.List.of(), java.util.List.of());
    }
    public RulebookActionDefinitionResult {
        if (candidateId == null || candidateId.isBlank()
                || !java.util.Set.of("COMPLETED", "INSUFFICIENT_EVIDENCE", "FAILED").contains(status))
            throw new IllegalArgumentException("assigned action result is invalid");
        if ("COMPLETED".equals(status) != (definition != null))
            throw new IllegalArgumentException("completed action result must contain exactly one definition");
        if (!"COMPLETED".equals(status) && (reason == null || reason.isBlank()))
            throw new IllegalArgumentException("incomplete action result requires a reason");
        neededFields = neededFields == null ? java.util.List.of() : java.util.List.copyOf(neededFields);
        sharedRules = sharedRules == null ? java.util.List.of() : java.util.List.copyOf(sharedRules);
        sharedRuleRefs = sharedRuleRefs == null ? java.util.List.of() : java.util.List.copyOf(sharedRuleRefs);
        if (neededFields.stream().anyMatch(field -> field == null || field.isBlank())
                || neededFields.stream().distinct().count() != neededFields.size())
            throw new IllegalArgumentException("needed fields must be unique nonblank field names");
    }
}
