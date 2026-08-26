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
        List<String> violations = new ArrayList<>();
        for (AdventurePlanEvidence evidence : stage.evidence()) {
            if (authoritative.stream().noneMatch(source -> matches(evidence, source))) {
                violations.add("story stage contains unknown source evidence");
            }
        }
        String source = authoritative.stream()
                .filter(item -> !nonNarrativeDocumentIds.contains(item.documentId()))
                .map(AdventureStoryPlanGenerationPort.SourceCitation::quote)
                .reduce("", (left, right) -> left + " " + right);
        if (!stage.boss().isBlank() && !SourceClaimSupport.supports(source, stage.boss())) {
            violations.add("story stage boss is not supported by source evidence");
        }
        for (String reward : stage.rewards()) {
            if (!SourceClaimSupport.supports(source, reward)) {
                violations.add("story stage reward is not supported by source evidence: " + reward);
            }
        }
        for (String npcOrClue : stage.npcOrClues()) {
            if (!SourceClaimSupport.supports(source, npcOrClue)) {
                violations.add("story stage NPC or clue is not supported by source evidence: " + npcOrClue);
            }
        }
        for (String enemy : stage.enemies()) {
            if (!SourceClaimSupport.supports(source, enemy)) {
                violations.add("story stage enemy is not supported by source evidence: " + enemy);
            }
        }
        if (!supportsCondition(source, stage.transitionCondition())) {
            violations.add("story stage transition is not supported by source evidence");
            violations.add("stage " + stage.position() + " transitionCondition is not supported by source evidence");
        }
        if (!supportsCondition(source, stage.clearCondition())) {
            violations.add("stage " + stage.position() + " clearCondition is not supported by source evidence");
        }
        if (!stage.failureCondition().isBlank() && !supportsCondition(source, stage.failureCondition())) {
            violations.add("stage " + stage.position() + " failureCondition is not supported by source evidence");
        }
        for (String ending : stage.endingIds()) {
            if (!SourceClaimSupport.structuralTarget(ending) && !SourceClaimSupport.supports(source, ending)) {
                violations.add("story stage ending is not supported by source evidence: " + ending);
            }
        }
        for (String branch : stage.branchIds()) {
            if (!SourceClaimSupport.structuralTarget(branch) && !SourceClaimSupport.supports(source, branch)) {
                violations.add("story stage transition is not supported by source evidence: " + branch);
            }
        }
        for (String target : stage.branchTargets().values()) {
            if (!SourceClaimSupport.structuralTarget(target) && !SourceClaimSupport.supports(source, target)) {
                violations.add("story stage transition is not supported by source evidence: " + target);
            }
        }
        return List.copyOf(violations);
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
