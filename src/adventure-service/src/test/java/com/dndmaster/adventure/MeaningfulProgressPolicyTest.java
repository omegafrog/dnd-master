package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.MeaningfulProgressCategory;
import com.dndmaster.adventure.application.runtime.MeaningfulProgressPolicy;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeaningfulProgressPolicyTest {
    private final MeaningfulProgressPolicy policy = new MeaningfulProgressPolicy();
    private final AdventureContext previous = new AdventureContext("scene", "npc", null, "old outcome");

    @Test
    void accepts_each_progress_category() {
        assertTrue(policy.evaluate("move", plan("next", "npc", "new outcome", List.of()), previous, List.of("new outcome"))
                .contains(MeaningfulProgressCategory.WORLD_STATE));
        assertTrue(policy.evaluate("search", plan("scene", "npc", "same", List.of("secret")), previous, List.of("old outcome"))
                .contains(MeaningfulProgressCategory.INFORMATION));
        assertTrue(policy.evaluate("act", plan("scene", "npc", "new outcome", List.of()), previous, List.of("new outcome"))
                .contains(MeaningfulProgressCategory.OUTCOME));
        assertTrue(policy.evaluate("choose", plan("scene", "npc", "DECISION_REQUIRED: A) fight or B) flee", List.of()), previous, List.of("old outcome"))
                .contains(MeaningfulProgressCategory.DECISION));
        assertTrue(policy.evaluate("search", plan("scene", "npc", "숨은 위험을 확인하려면 지혜(감지) 판정이 필요합니다.", List.of()), previous, List.of("old outcome"))
                .contains(MeaningfulProgressCategory.CHECK));
        assertTrue(policy.evaluate("advance", plan("scene", "npc", "same", List.of()), previous, List.of("old outcome"), true)
                .contains(MeaningfulProgressCategory.PROGRESS));
    }

    @Test
    void rejects_empty_progress_and_unresolved_decision() {
        TurnPlan unchanged = plan("scene", "npc", "same", List.of());
        IllegalStateException noProgress = assertThrows(IllegalStateException.class,
                () -> policy.evaluate("repeat", unchanged, previous, List.of("old outcome")));
        assertTrue(noProgress.getMessage().startsWith("NO_MEANINGFUL_PROGRESS"));

        IllegalStateException decision = assertThrows(IllegalStateException.class,
                () -> policy.evaluate("choose", plan("scene", "npc", "DECISION_REQUIRED", List.of()), previous, List.of("old outcome")));
        assertTrue(decision.getMessage().startsWith("DECISION_REQUIRED"));
    }

    @Test
    void rejects_missing_intent() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> policy.evaluate(" ", plan("next", "npc", "new", List.of()), previous, List.of("new")));
        assertEquals("NO_MEANINGFUL_PROGRESS: player intent is missing", failure.getMessage());
    }

    private static TurnPlan plan(String scene, String npcState, String judgment, List<String> facts) {
        return new TurnPlan(scene, npcState, judgment, facts, List.of());
    }
}
