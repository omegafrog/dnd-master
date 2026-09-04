package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus;
import com.dndmaster.adventure.domain.adventure.AdventureStageType;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.StageRole;
import com.dndmaster.adventure.domain.scenario.ScenarioEntryResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Materializes the preparation entry decision without introducing runtime state. */
public final class AdventureStoryPlanStageRolePolicy {
    private AdventureStoryPlanStageRolePolicy() { }

    public static List<AdventureStoryPlanStage> materialize(ScenarioEntryResult entry,
            List<AdventureStoryPlanStage> sourceStages) {
        if (entry == null || !entry.requiresPrologue()) {
            return sourceStages == null ? List.of() : sourceStages.stream()
                    .map(stage -> stage.withStageRole(StageRole.NORMAL)).toList();
        }
        List<AdventureStoryPlanStage> materialized = new ArrayList<>();
        materialized.add(prologue(entry));
        for (AdventureStoryPlanStage stage : sourceStages == null ? List.<AdventureStoryPlanStage>of() : sourceStages) {
            materialized.add(stage.withPosition(stage.position() + 1).withStageRole(StageRole.NORMAL));
        }
        return List.copyOf(materialized);
    }

    public static AdventureStoryPlanStage prologue(ScenarioEntryResult entry) {
        return new AdventureStoryPlanStage(1,
                "Prologue: " + entry.entryPoint(),
                entry.startPremise(),
                "The party needs a safe first foothold.",
                "Reach " + entry.entryPoint(),
                List.of(entry.sourceAnchor()), List.of(), List.of(), AdventureStageType.EVENT,
                entry.sourceAnchor(), null, "", "", List.of(), "", "Reach " + entry.entryPoint(), "",
                List.of(), List.of(), List.of(), AdventureGroundingStatus.GROUNDED, List.of(), "UNAVAILABLE", null)
                .withStageRole(StageRole.PROLOGUE)
                .withSchemaVersion(AdventureStoryPlanStage.CURRENT_SCHEMA_VERSION);
    }

    public static List<String> validatePrologue(AdventureStoryPlanStage stage, String sourceAnchor) {
        List<String> violations = new ArrayList<>();
        if (stage == null) return List.of();
        String anchor = sourceAnchor == null ? "" : sourceAnchor.trim().toLowerCase(Locale.ROOT);
        String content = String.join(" ", stage.title(), stage.goal(), stage.conflict(), stage.transitionCondition(),
                stage.location(), String.join(" ", stage.npcOrClues())).toLowerCase(Locale.ROOT);
        if (anchor.isBlank() || !content.contains(anchor)) violations.add("prologue requires a source or world anchor");
        String[] campaignScale = {"main quest", "villain", "secret", "faction", "campaign"};
        for (String marker : campaignScale) {
            if (content.contains(marker)) {
                violations.add("prologue must remain connector-scale");
                break;
            }
        }
        if (!stage.endingIds().isEmpty() || !stage.branchIds().isEmpty() || !stage.enemies().isEmpty()
                || !stage.boss().isBlank()) violations.add("prologue must remain connector-scale");
        return List.copyOf(violations);
    }
}
