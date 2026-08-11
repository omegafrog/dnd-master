package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanGraphValidator;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdventureStoryPlanGraphValidatorTest {
    private static final AdventurePlanConfiguration CONFIG = new AdventurePlanConfiguration(2, AdventureLength.SHORT);

    @Test
    void accepts_linear_plan_with_explicit_branch_targets() {
        var first = new AdventureStoryPlanStage(1, "Town", "Start", "Threat", "Move", List.of(), List.of("ending-a"), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureStageType.TOWN, "Town", null, "", "", List.of(), "", "Move", "", List.of(),
                List.of("to-dungeon", "to-end"), List.of(), com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.AI_SUGGESTION,
                List.of(), "UNAVAILABLE", null, Map.of("to-dungeon", "stage:2", "to-end", "ending-b"));
        var second = new AdventureStoryPlanStage(2, "Dungeon", "Enter", "Guard", "Win", List.of(), List.of("ending-b"));

        assertDoesNotThrow(() -> AdventureStoryPlanGraphValidator.validate(List.of(first, second), CONFIG));
    }

    @Test
    void rejects_unknown_branch_target_ending() {
        var first = new AdventureStoryPlanStage(1, "Town", "Start", "Threat", "Move", List.of(), List.of("ending-a"), List.of(),
                com.dndmaster.adventure.domain.adventure.AdventureStageType.TOWN, "Town", null, "", "", List.of(), "", "Move", "", List.of(),
                List.of("to-end"), List.of(), com.dndmaster.adventure.domain.adventure.AdventureGroundingStatus.AI_SUGGESTION,
                List.of(), "UNAVAILABLE", null, Map.of("to-end", "ending-missing"));
        var second = new AdventureStoryPlanStage(2, "Dungeon", "Enter", "Guard", "Win", List.of(), List.of("ending-b"));

        assertThrows(IllegalArgumentException.class, () -> AdventureStoryPlanGraphValidator.validate(List.of(first, second), CONFIG));
    }
}
