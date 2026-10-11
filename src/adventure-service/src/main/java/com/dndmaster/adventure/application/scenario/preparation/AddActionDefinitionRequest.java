package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.StructuredSpellDefinition;
import java.util.UUID;

public record AddActionDefinitionRequest(long expectedRevision, String name, UUID sourceDocumentId,
        String sourceLocator, StructuredSpellDefinition.ActionKind actionKind, String level, String castingTime,
        String rangeArea, String components, String duration, String school, String attackSave, String damageEffect,
        StructuredSpellDefinition.RangeProfile rangeProfile,
        StructuredSpellDefinition.TargetingProfile targetingProfile, boolean required) {
    public AddActionDefinitionRequest {
        if (expectedRevision < 0 || name == null || name.isBlank() || name.length() > 200
                || sourceDocumentId == null || sourceLocator == null || sourceLocator.isBlank()
                || sourceLocator.length() > 1000 || actionKind == null || rangeProfile == null || targetingProfile == null) {
            throw new IllegalArgumentException("action definition requires name, authorized source locator, kind and profiles");
        }
        if (actionKind == StructuredSpellDefinition.ActionKind.SPELL) {
            for (String value : new String[] { level, castingTime, rangeArea, components, duration, school, attackSave, damageEffect }) {
                if (value == null || value.isBlank() || value.length() > 2000)
                    throw new IllegalArgumentException("spell action fields must contain 1 to 2000 characters");
            }
        }
    }
}
