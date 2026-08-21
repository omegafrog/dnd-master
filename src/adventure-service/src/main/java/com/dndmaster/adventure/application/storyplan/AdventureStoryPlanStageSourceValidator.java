package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Rejects core tactical-stage facts that are not supported by authoritative source evidence. */
public final class AdventureStoryPlanStageSourceValidator {
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
        if (!supportsCondition(source, stage.transitionCondition())
                || !supportsCondition(source, stage.clearCondition())
                || (!stage.failureCondition().isBlank() && !supportsCondition(source, stage.failureCondition()))) {
            violations.add("story stage transition is not supported by source evidence");
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
        return evidence.documentType().equals(source.documentType())
                && evidence.documentId().equals(source.documentId())
                && evidence.extractionVersion() == source.extractionVersion()
                && evidence.locator().equals(source.locator())
                && evidence.quote().equals(source.quote())
                && evidence.confidence() <= source.confidence();
    }

}
