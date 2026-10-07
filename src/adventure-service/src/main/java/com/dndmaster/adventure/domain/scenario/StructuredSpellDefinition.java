package com.dndmaster.adventure.domain.scenario;

import java.util.List;
import java.util.Objects;

public record StructuredSpellDefinition(
        String id,
        String name,
        java.util.UUID sourceDocumentId,
        String sourceLocator,
        String sourceVersion,
        long extractionVersion,
        String level,
        String castingTime,
        String rangeArea,
        String components,
        String duration,
        String school,
        String attackSave,
        String damageEffect,
        List<Integer> ownerPlanNumbers,
        String ownerEvidence,
        boolean executable,
        ReviewStatus reviewStatus) {
    public StructuredSpellDefinition {
        id = required(id, "spell id");
        name = required(name, "spell name");
        sourceDocumentId = Objects.requireNonNull(sourceDocumentId, "spell source document id must not be null");
        sourceLocator = required(sourceLocator, "spell source locator");
        sourceVersion = required(sourceVersion, "spell source version");
        if (extractionVersion <= 0) throw new IllegalArgumentException("spell extraction version must be positive");
        level = required(level, "spell level");
        castingTime = required(castingTime, "spell casting time");
        rangeArea = required(rangeArea, "spell range or area");
        components = required(components, "spell components");
        duration = required(duration, "spell duration");
        school = required(school, "spell school");
        attackSave = required(attackSave, "spell attack or save");
        damageEffect = required(damageEffect, "spell effect");
        ownerPlanNumbers = List.copyOf(Objects.requireNonNull(ownerPlanNumbers, "spell owners must not be null"));
        if (ownerPlanNumbers.stream().anyMatch(number -> number < 368 || number > 372)) {
            throw new IllegalArgumentException("spell owner must be an approved execution plan");
        }
        ownerEvidence = required(ownerEvidence, "spell owner evidence");
        reviewStatus = Objects.requireNonNull(reviewStatus, "spell review status must not be null");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    public enum ReviewStatus { PENDING }
}
