package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Rejects core tactical-stage facts that are not supported by authoritative source evidence. */
public final class AdventureStoryPlanStageSourceValidator {
    /**
     * Ensures the model does not silently ground the whole plan in only one
     * family of supplied sources. Storybook and rulebook citations have
     * different authority, so both must be represented when both are
     * available to the generator.
     */
    public List<String> validateCitationCoverage(
            List<AdventureStoryPlanStage> stages,
            List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative) {
        Set<String> usedTypes = stages.stream()
                .flatMap(stage -> stage.evidence().stream())
                .map(AdventurePlanEvidence::documentType)
                .map(AdventureStoryPlanStageSourceValidator::normalizeDocumentType)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> availableTypes = authoritative.stream()
                .map(AdventureStoryPlanGenerationPort.SourceCitation::documentType)
                .map(AdventureStoryPlanStageSourceValidator::normalizeDocumentType)
                .collect(java.util.stream.Collectors.toSet());

        List<String> violations = new ArrayList<>();
        if (authoritative.stream().anyMatch(source -> !hasPublishedProvenance(source))) {
            violations.add("story plan contains evidence without published provenance");
        }
        for (String requiredType : List.of("STORYBOOK", "RULEBOOK")) {
            if (availableTypes.contains(requiredType) && !usedTypes.contains(requiredType)) {
                violations.add("story plan must cite at least one " + requiredType + " source");
            }
        }
        return List.copyOf(violations);
    }

    public List<String> validate(
            AdventureStoryPlanStage stage,
            List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative) {
        return validate(stage, authoritative, Set.of());
    }

    public List<String> validate(
            AdventureStoryPlanStage stage,
            List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
            Set<UUID> nonNarrativeDocumentIds) {
        return validateStructured(stage, authoritative, nonNarrativeDocumentIds).stream()
                .map(AdventureStoryPlanProjectionViolation::sanitizedMessage).toList();
    }

    public List<AdventureStoryPlanProjectionViolation> validateStructured(
            AdventureStoryPlanStage stage,
            List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative,
            Set<UUID> nonNarrativeDocumentIds) {
        List<AdventureStoryPlanProjectionViolation> structured = new ArrayList<>();
        int stageIndex = Math.max(0, stage.position() - 1);
        for (AdventurePlanEvidence evidence : stage.evidence()) {
            if (authoritative.stream().noneMatch(source -> matches(evidence, source))) {
                structured.add(new AdventureStoryPlanProjectionViolation(
                        "UNKNOWN_SOURCE_EVIDENCE", stage.position(), "stages[" + stageIndex + "].evidence[*]",
                        evidence.locator(), evidence.documentType() + ":" + evidence.documentId() + ":" + evidence.extractionVersion() + ":" + evidence.locator(),
                        AdventureStoryPlanProjectionViolation.Repairability.SOURCE_EVIDENCE_INSUFFICIENT,
                        "stage " + stage.position() + " evidence is not registered"));
            }
        }
        String source = authoritative.stream()
                .filter(item -> !nonNarrativeDocumentIds.contains(item.documentId()))
                .map(AdventureStoryPlanGenerationPort.SourceCitation::quote)
                .reduce("", (left, right) -> left + " " + right);
        if (!stage.boss().isBlank() && !SourceClaimSupport.supports(source, stage.boss())) {
            structured.add(unsupported(stage, "boss", stage.boss(), "stage " + stage.position() + " boss is not supported by source evidence"));
        }
        for (String reward : stage.rewards()) {
            if (!SourceClaimSupport.supports(source, reward)) {
                structured.add(unsupported(stage, "rewards[*]", reward, "stage " + stage.position() + " reward is not supported by source evidence"));
            }
        }
        for (String npcOrClue : stage.npcOrClues()) {
            if (!SourceClaimSupport.supports(source, npcOrClue)) {
                structured.add(unsupported(stage, "npcOrClues[*]", npcOrClue, "stage " + stage.position() + " NPC or clue is not supported by source evidence"));
            }
        }
        for (String enemy : stage.enemies()) {
            if (!SourceClaimSupport.supports(source, enemy)) {
                structured.add(unsupported(stage, "enemies[*]", enemy, "stage " + stage.position() + " enemy is not supported by source evidence"));
            }
        }
        if (!supportsCondition(source, stage.transitionCondition())) {
            structured.add(unsupported(stage, "transitionCondition", stage.transitionCondition(),
                    "stage " + stage.position() + " transitionCondition is not supported by source evidence"));
        }
        if (!supportsCondition(source, stage.clearCondition())) {
            structured.add(unsupported(stage, "clearCondition", stage.clearCondition(),
                    "stage " + stage.position() + " clearCondition is not supported by source evidence"));
        }
        if (!stage.failureCondition().isBlank() && !supportsCondition(source, stage.failureCondition())) {
            structured.add(unsupported(stage, "failureCondition", stage.failureCondition(),
                    "stage " + stage.position() + " failureCondition is not supported by source evidence"));
        }
        for (String ending : stage.endingIds()) {
            if (!SourceClaimSupport.structuralTarget(ending) && !SourceClaimSupport.supports(source, ending)) {
                structured.add(unsupported(stage, "endingIds[*]", ending, "stage " + stage.position() + " ending is not supported by source evidence"));
            }
        }
        for (String branch : stage.branchIds()) {
            if (!SourceClaimSupport.structuralTarget(branch) && !SourceClaimSupport.supports(source, branch)) {
                structured.add(unsupported(stage, "branchIds[*]", branch, "stage " + stage.position() + " branch is not supported by source evidence"));
            }
        }
        for (String target : stage.branchTargets().values()) {
            if (!SourceClaimSupport.structuralTarget(target) && !SourceClaimSupport.supports(source, target)) {
                structured.add(unsupported(stage, "branchTargets[*]", target, "stage " + stage.position() + " branch target is not supported by source evidence"));
            }
        }
        return List.copyOf(structured);
    }

    private static AdventureStoryPlanProjectionViolation unsupported(
            AdventureStoryPlanStage stage, String field, String rejectedValue, String message) {
        return new AdventureStoryPlanProjectionViolation(
                "SOURCE_CLAIM_UNSUPPORTED", stage.position(), "stages[" + Math.max(0, stage.position() - 1) + "]." + field,
                rejectedValue, "authoritative source evidence", AdventureStoryPlanProjectionViolation.Repairability.SOURCE_EVIDENCE_INSUFFICIENT,
                message);
    }

    private static boolean supportsCondition(String source, String condition) {
        if (SourceClaimSupport.supports(source, condition)) return true;
        if (condition == null || condition.isBlank()) return false;
        String normalized = SourceClaimSupport.normalize(condition);
        // These phrases describe the act of progressing the table, not a claim about
        // the setting. They remain safe when the supplied source contains only map
        // metadata or other sparse evidence.
        return normalized.matches(".*(단서|정보|조사|확보|확인|다음|이동|진행|도달|완료|결정|경로|목표|조건|결과).*")
                && !normalized.matches(".*(드래곤|용|고블린|오크|왕|여왕|성|탑|지하실|보물|왕관).*");
    }

    private static boolean matches(
            AdventurePlanEvidence evidence,
            AdventureStoryPlanGenerationPort.SourceCitation source) {
        return source.provenance() != null
                && evidence.documentType().equals(source.documentType())
                && evidence.documentId().equals(source.documentId())
                && evidence.extractionVersion() == source.extractionVersion()
                && evidence.locator().equals(source.locator())
                && evidence.quote().equals(source.quote())
                && evidence.confidence() <= source.confidence();
    }

    private static String normalizeDocumentType(String documentType) {
        return documentType == null ? "" : documentType.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean hasPublishedProvenance(AdventureStoryPlanGenerationPort.SourceCitation source) {
        return source.provenance() != null
                && source.documentId().equals(source.provenance().documentId().value())
                && source.extractionVersion() == source.provenance().extractionVersion()
                && source.locator().equals(source.provenance().locator());
    }

}
