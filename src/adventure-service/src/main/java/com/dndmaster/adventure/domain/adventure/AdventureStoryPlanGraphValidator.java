package com.dndmaster.adventure.domain.adventure;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates authored branch destinations without requiring a particular narrative shape. */
public final class AdventureStoryPlanGraphValidator {
    private AdventureStoryPlanGraphValidator() {}

    public static void validate(List<AdventureStoryPlanStage> stages, AdventurePlanConfiguration configuration) {
        if (stages == null || stages.isEmpty()) throw new IllegalArgumentException("story plan requires stages");
        Set<Integer> positions = new HashSet<>();
        for (AdventureStoryPlanStage stage : stages) {
            if (!positions.add(stage.position()) || stage.position() < 1 || stage.position() > stages.size()) {
                throw new IllegalArgumentException("story plan stage positions must be unique and contiguous");
            }
            validateTargets(stage, stages.size());
        }
        if (positions.size() != stages.size()) {
            throw new IllegalArgumentException("story plan stage positions must be unique and contiguous");
        }
    }

    private static void validateTargets(AdventureStoryPlanStage stage, int stageCount) {
        Map<String, String> targets = stage.branchTargets();
        if (!stage.branchIds().containsAll(targets.keySet())) throw new IllegalArgumentException("branch target is not declared");
        for (String branch : stage.branchIds()) {
            String target = targets.get(branch);
            if (target == null || target.isBlank()) continue;
            if (target.startsWith("stage:")) {
                try {
                    int position = Integer.parseInt(target.substring("stage:".length()));
                    if (position <= stage.position() || position > stageCount) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("branch target stage is invalid");
                }
            }
        }
    }
}
