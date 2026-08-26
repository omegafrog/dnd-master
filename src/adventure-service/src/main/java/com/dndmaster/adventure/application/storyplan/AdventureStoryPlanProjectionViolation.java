package com.dndmaster.adventure.application.storyplan;

import java.util.Objects;

/** A safe, field-level description of a rejected execution projection. */
public record AdventureStoryPlanProjectionViolation(
        String code,
        Integer stagePosition,
        String fieldPath,
        String rejectedValue,
        String citationContext,
        Repairability repairability,
        String sanitizedMessage) {

    public enum Repairability {
        REPAIRABLE,
        REGENERATE_REQUIRED,
        SOURCE_EVIDENCE_INSUFFICIENT,
        SYSTEM_CONTRACT_ERROR
    }

    public AdventureStoryPlanProjectionViolation {
        code = required(code, "violation code");
        fieldPath = required(fieldPath, "violation field path");
        repairability = Objects.requireNonNull(repairability, "violation repairability");
        rejectedValue = compact(rejectedValue);
        citationContext = compact(citationContext);
        sanitizedMessage = compact(sanitizedMessage);
        if (sanitizedMessage.isBlank()) sanitizedMessage = code + " at " + fieldPath;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    /** Keeps diagnostics single-line and bounded; source quotes and full candidates never belong here. */
    private static String compact(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" +", " ").trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256) + "...";
    }
}
