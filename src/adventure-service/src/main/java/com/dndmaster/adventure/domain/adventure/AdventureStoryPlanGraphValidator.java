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
        Set<String> endings = new HashSet<>();
        for (AdventureStoryPlanStage stage : stages) {
            if (!positions.add(stage.position()) || stage.position() < 1 || stage.position() > stages.size()) {
                throw new IllegalArgumentException("story plan stage positions must be unique and contiguous");
            }
            endings.addAll(stage.endingIds());
            validateTargets(stage, stages.size(), endings);
        }
        if (positions.size() != stages.size() || endings.size() != configuration.endingCount()) {
            throw new IllegalArgumentException("story plan endings do not match configuration");
        }
        if (stages.get(stages.size() - 1).endingIds().isEmpty()) {
            throw new IllegalArgumentException("final story stage must converge to an ending");
        }
        for (AdventureStoryPlanStage stage : stages) {
            for (String target : stage.branchTargets().values()) {
                if (!target.startsWith("stage:") && !endings.contains(target)) {
                    throw new IllegalArgumentException("branch target ending is unknown");
                }
            }
        }
        if (!endings.stream().allMatch(ending -> stages.stream().anyMatch(stage -> stage.endingIds().contains(ending)))) {
            throw new IllegalArgumentException("story plan contains an unreachable ending");
        }
    }

    private static void validateTargets(AdventureStoryPlanStage stage, int stageCount, Set<String> endings) {
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
            } else if (!endings.contains(target) && !stage.endingIds().contains(target)) {
                // Ending IDs may be discovered on a later stage; they are checked after all stages are scanned.
            }
        }
    }
}
