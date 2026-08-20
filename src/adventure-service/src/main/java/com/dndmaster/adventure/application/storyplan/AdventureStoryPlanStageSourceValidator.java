package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rejects core tactical-stage facts that are not supported by authoritative source evidence. */
public final class AdventureStoryPlanStageSourceValidator {
    private static final Pattern STRUCTURAL_TARGET = Pattern.compile("(?:ending|end)(?:-\\d+)?|stage-\\d+|\\d+");

    public List<String> validate(
            AdventureStoryPlanStage stage,
            List<AdventureStoryPlanGenerationPort.SourceCitation> authoritative) {
        List<String> violations = new ArrayList<>();
        for (AdventurePlanEvidence evidence : stage.evidence()) {
            if (authoritative.stream().noneMatch(source -> matches(evidence, source))) {
                violations.add("story stage contains unknown source evidence");
            }
        }
        String source = stage.evidence().stream()
                .map(AdventurePlanEvidence::quote)
                .reduce("", (left, right) -> left + " " + right);
        if (!stage.boss().isBlank() && !supports(source, stage.boss())) {
            violations.add("story stage boss is not supported by source evidence");
        }
        for (String reward : stage.rewards()) {
            if (!supports(source, reward)) {
                violations.add("story stage reward is not supported by source evidence: " + reward);
            }
        }
        if (!supports(source, stage.transitionCondition())
                || !supports(source, stage.clearCondition())
                || (!stage.failureCondition().isBlank() && !supports(source, stage.failureCondition()))) {
            violations.add("story stage transition is not supported by source evidence");
        }
        for (String ending : stage.endingIds()) {
            if (!structuralTarget(ending) && !supports(source, ending)) {
                violations.add("story stage ending is not supported by source evidence: " + ending);
            }
        }
        for (String branch : stage.branchIds()) {
            if (!structuralTarget(branch) && !supports(source, branch)) {
                violations.add("story stage transition is not supported by source evidence: " + branch);
            }
        }
        for (String target : stage.branchTargets().values()) {
            if (!structuralTarget(target) && !supports(source, target)) {
                violations.add("story stage transition is not supported by source evidence: " + target);
            }
        }
        return List.copyOf(violations);
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

    private static boolean supports(String source, String claim) {
        String normalizedClaim = normalize(claim);
        return !normalizedClaim.isBlank() && normalize(source).contains(normalizedClaim);
    }

    private static boolean structuralTarget(String value) {
        return STRUCTURAL_TARGET.matcher(normalize(value).replace(' ', '-')).matches();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
