package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.stream.Collectors;

/** Formats the authored story plan into the GM-only runtime context. */
public final class AdventureStoryPlanRuntimeContext {
    private AdventureStoryPlanRuntimeContext() {}

    public static String format(AdventureStoryPlan plan) {
        if (plan == null) return "";
        if (plan.stages().isEmpty()) return "planStatus=" + plan.status() + "; planVersion=" + plan.version();
        AdventureStoryPlanStage stage = plan.stages().get(plan.currentStage());
        String branches = stage.branchIds().stream().collect(Collectors.joining(","));
        String enemies = stage.enemies().stream().collect(Collectors.joining(","));
        String rewards = stage.rewards().stream().collect(Collectors.joining(","));
        String evidence = stage.evidence().stream().map(item -> item.documentType() + ":" + item.locator()).collect(Collectors.joining(","));
        return "planVersion=" + plan.version()
                + "; status=" + plan.status()
                + "; currentStage=" + stage.position()
                + "; stageType=" + stage.stageType()
                + "; title=" + stage.title()
                + "; location=" + stage.location()
                + "; goal=" + stage.goal()
                + "; conflict=" + stage.conflict()
                + "; clearCondition=" + stage.clearCondition()
                + "; failureCondition=" + stage.failureCondition()
                + "; enemies=" + enemies
                + "; boss=" + stage.boss()
                + "; rewards=" + rewards
                + "; availableBranches=" + branches
                + "; branchTargets=" + stage.branchTargets()
                + "; map=" + stage.mapAssetId() + "@" + stage.mapAssetLocator()
                + "; grounding=" + stage.groundingStatus()
                + "; evidence=" + evidence;
    }
}
