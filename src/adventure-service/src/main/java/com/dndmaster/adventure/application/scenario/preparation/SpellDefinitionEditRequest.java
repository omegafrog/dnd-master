package com.dndmaster.adventure.application.scenario.preparation;

public record SpellDefinitionEditRequest(long expectedRevision, String level, String castingTime, String rangeArea,
        String components, String duration, String school, String attackSave, String damageEffect) {
    public SpellDefinitionEditRequest {
        if (expectedRevision < 0) throw new IllegalArgumentException("expected revision must not be negative");
        validate(level, "level");
        validate(castingTime, "casting time");
        validate(rangeArea, "range or area");
        validate(components, "components");
        validate(duration, "duration");
        validate(school, "school");
        validate(attackSave, "attack or save");
        validate(damageEffect, "effect");
    }

    private static void validate(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 2000) {
            throw new IllegalArgumentException(field + " must contain 1 to 2000 characters");
        }
    }
}
