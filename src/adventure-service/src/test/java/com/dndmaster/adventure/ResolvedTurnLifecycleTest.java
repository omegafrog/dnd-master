package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.runtime.ResolvedTurnPlan;
import com.dndmaster.adventure.application.runtime.EffectivePromptLineage;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.TurnPlan;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResolvedTurnLifecycleTest {
    @Test
    void resolved_artifact_is_immutable_and_only_moves_forward() {
        TurnPlan plan = new TurnPlan("scene", "npc", "success", List.of("visible"), List.of("secret"));
        ResolvedTurnPlan resolved = ResolvedTurnPlan.of(plan, List.of("roll=success"));

        assertEquals(RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, resolved.lifecycle());
        assertThrows(UnsupportedOperationException.class, () -> resolved.outcomes().add("another"));
        assertEquals(RuntimeTurnLifecycle.PRESENTED, resolved.presented().lifecycle());
    }

    @Test
    void resolved_artifact_keeps_effective_prompt_lineage_through_presentation() {
        EffectivePromptLineage lineage = new EffectivePromptLineage("WRITER", "1.1.0", "model-writer", "run-42", "1.0.0");
        ResolvedTurnPlan resolved = ResolvedTurnPlan.of(new TurnPlan("scene", "npc", "success", List.of(), List.of()), List.of("roll"))
                .withPromptLineage(lineage);

        assertEquals(lineage, resolved.promptLineage());
        assertEquals(lineage, resolved.presented().promptLineage());
    }
}
