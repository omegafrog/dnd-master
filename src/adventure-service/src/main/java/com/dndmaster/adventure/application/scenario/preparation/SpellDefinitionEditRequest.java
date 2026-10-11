package com.dndmaster.adventure.application.scenario.preparation;

public record SpellDefinitionEditRequest(long expectedRevision, String level, String castingTime, String rangeArea,
        String components, String duration, String school, String attackSave, String damageEffect, Boolean required,
        com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ActionKind actionKind,
        com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.RangeProfile rangeProfile,
        com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.TargetingProfile targetingProfile,
        Boolean active) {
    public SpellDefinitionEditRequest(long expectedRevision, String level, String castingTime, String rangeArea,
            String components, String duration, String school, String attackSave, String damageEffect) {
        this(expectedRevision, level, castingTime, rangeArea, components, duration, school, attackSave, damageEffect, null, null, null, null, null);
    }
    public SpellDefinitionEditRequest(long expectedRevision, String level, String castingTime, String rangeArea,
            String components, String duration, String school, String attackSave, String damageEffect, Boolean required) {
        this(expectedRevision, level, castingTime, rangeArea, components, duration, school, attackSave,
                damageEffect, required, null, null, null, null);
    }
    public SpellDefinitionEditRequest {
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision must not be negative");
        validate(rangeArea, "range or area");
        if (actionKind == null || actionKind == com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition.ActionKind.SPELL) {
            validate(level, "level");
            validate(castingTime, "casting time");
            validate(components, "components");
            validate(duration, "duration");
            validate(school, "school");
            validate(attackSave, "attack or save");
            validate(damageEffect, "effect");
        }
    }

    private static void validate(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 2000) {
            throw new IllegalArgumentException(field + " must contain 1 to 2000 characters");
        }
    }
}
