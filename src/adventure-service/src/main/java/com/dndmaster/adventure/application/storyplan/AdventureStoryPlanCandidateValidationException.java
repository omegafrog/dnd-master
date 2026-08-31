package com.dndmaster.adventure.application.storyplan;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Typed adapter result for an invalid AI outline candidate, distinct from provider failure. */
public final class AdventureStoryPlanCandidateValidationException extends RuntimeException {
    private final List<String> violations;
    private final List<AdventureStoryPlanProjectionViolation> structuredViolations;
    private final String rejectedCandidate;

    public AdventureStoryPlanCandidateValidationException(List<String> violations) {
        this(violations, null);
    }

    public AdventureStoryPlanCandidateValidationException(List<String> violations, String rejectedCandidate) {
        this(legacyViolations(violations), rejectedCandidate, true);
    }

    public AdventureStoryPlanCandidateValidationException(
            List<AdventureStoryPlanProjectionViolation> structuredViolations, String rejectedCandidate,
            boolean structured) {
        super(message(structuredViolations));
        if (structuredViolations == null || structuredViolations.isEmpty()) {
            throw new IllegalArgumentException("candidate violations must not be empty");
        }
        this.structuredViolations = List.copyOf(structuredViolations);
        this.violations = this.structuredViolations.stream()
                .map(AdventureStoryPlanProjectionViolation::sanitizedMessage).toList();
        this.rejectedCandidate = rejectedCandidate == null ? "" : rejectedCandidate;
    }

    public List<String> violations() {
        return violations;
    }

    public List<AdventureStoryPlanProjectionViolation> structuredViolations() {
        return structuredViolations;
    }

    public String rejectedCandidate() {
        return rejectedCandidate;
    }

    public boolean hasRejectedCandidate() {
        return !rejectedCandidate.isBlank();
    }

    private static String message(List<AdventureStoryPlanProjectionViolation> values) {
        return values == null ? "candidate validation failed" : values.stream()
                .map(AdventureStoryPlanProjectionViolation::sanitizedMessage)
                .reduce((left, right) -> left + "; " + right).orElse("candidate validation failed");
    }

    private static List<AdventureStoryPlanProjectionViolation> legacyViolations(List<String> values) {
        Objects.requireNonNull(values, "candidate violations must not be null");
        if (values.isEmpty()) throw new IllegalArgumentException("candidate violations must not be empty");
        return values.stream().map(AdventureStoryPlanCandidateValidationException::legacyViolation).toList();
    }

    public static AdventureStoryPlanProjectionViolation legacyViolation(String raw) {
        String message = raw == null || raw.isBlank() ? "candidate validation failed" : raw.trim();
        String normalized = message.toLowerCase(Locale.ROOT);
        boolean unknownCitation = normalized.contains("unknown citation")
                || normalized.contains("citation key is not registered");
        boolean unsupportedCombatParticipant = normalized.contains("unsupported combat participant")
                || normalized.contains("combat participant is not supported")
                || normalized.contains("combat participant source unsupported");
        java.util.regex.Matcher stageMatcher = java.util.regex.Pattern.compile("(?i)stage\\s+(\\d+)").matcher(message);
        Integer stagePosition = stageMatcher.find() ? Integer.valueOf(stageMatcher.group(1)) : null;
        AdventureStoryPlanProjectionViolation.Repairability repairability = normalized.contains("serialized projection")
                || normalized.contains("full projection candidate")
                ? AdventureStoryPlanProjectionViolation.Repairability.SYSTEM_CONTRACT_ERROR
                : unknownCitation || unsupportedCombatParticipant
                ? AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE
                : normalized.contains("citation")
                || normalized.contains("source") || normalized.contains("map")
                ? AdventureStoryPlanProjectionViolation.Repairability.SOURCE_EVIDENCE_INSUFFICIENT
                : normalized.contains("not supported")
                        ? AdventureStoryPlanProjectionViolation.Repairability.SOURCE_EVIDENCE_INSUFFICIENT
                : normalized.contains("invalid json") || normalized.contains("malformed") || normalized.contains("missing")
                        || normalized.contains("required") || normalized.contains("stage count")
                        ? AdventureStoryPlanProjectionViolation.Repairability.REGENERATE_REQUIRED
                : AdventureStoryPlanProjectionViolation.Repairability.REPAIRABLE;
        String field = unknownCitation ? "stages[*].evidence[*].citationKey"
                : unsupportedCombatParticipant ? "stages[*].combatSkeleton.participants[*].name"
                : normalized.contains("transitioncondition") ? "stages[*].transitionCondition"
                : normalized.contains("clearcondition") ? "stages[*].clearCondition"
                : normalized.contains("failurecondition") ? "stages[*].failureCondition"
                : normalized.contains("ending") ? "stages[*].endingIds" : "stages";
        if (stagePosition != null) field = field.replaceFirst("\\[\\*\\]", "[" + (stagePosition - 1) + "]");
        String code = unsupportedCombatParticipant ? "COMBAT_PARTICIPANT_SOURCE_UNSUPPORTED"
                : normalized.contains("citation") ? "CITATION_CONTRACT_VIOLATION"
                : normalized.contains("map") ? "MAP_CONTRACT_VIOLATION"
                : normalized.contains("missing") || normalized.contains("required") ? "REQUIRED_FIELD_MISSING"
                : "CANDIDATE_VALIDATION_FAILED";
        String detail = message.contains(":") ? message.substring(message.lastIndexOf(':') + 1).trim() : "";
        String safeMessage = message.contains(":") ? message.substring(0, message.indexOf(':')).trim() : message;
        String citationContext = normalized.contains("citation") ? detail : "";
        return new AdventureStoryPlanProjectionViolation(code, stagePosition, field, detail, citationContext, repairability, safeMessage);
    }
}
