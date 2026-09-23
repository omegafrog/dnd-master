package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.NarrativeSceneGroundingPolicy;
import com.dndmaster.adventure.application.runtime.RuntimePlan;
import com.dndmaster.adventure.application.runtime.RuntimeResolutionProposal;
import com.dndmaster.adventure.application.runtime.SituationProposal;
import com.dndmaster.adventure.application.runtime.SituationUpdateProposal;
import com.dndmaster.adventure.domain.runtime.CompletionProposal;
import com.dndmaster.adventure.domain.runtime.DisclosureState;
import com.dndmaster.adventure.domain.runtime.GameStateDelta;
import java.util.List;
import org.junit.jupiter.api.Test;

class NarrativeSceneGroundingPolicyTest {
    @Test
    void keeps_the_current_scene_for_a_map_entry_without_a_grounded_story_transition() {
        RuntimePlan plan = plan("map-room").withMapEntryRequested(true);

        RuntimePlan grounded = NarrativeSceneGroundingPolicy.apply(plan, "hall", RuntimeResolutionProposal.unchanged());

        assertEquals("hall", grounded.scene());
    }

    @Test
    void accepts_a_scene_label_when_a_situation_transition_was_grounded() {
        RuntimePlan plan = plan("cellar");
        SituationUpdateProposal update = SituationUpdateProposal.transition("cellar", "find the source", "rats", "escape");
        SituationProposal situation = new SituationProposal(update, SituationProposal.Basis.RAG, "story:cellar", true);
        RuntimeResolutionProposal proposal = new RuntimeResolutionProposal(GameStateDelta.empty(), DisclosureState.empty(),
                update, List.of(), CompletionProposal.continueAdventure(), situation);

        RuntimePlan grounded = NarrativeSceneGroundingPolicy.apply(plan, "hall", proposal);

        assertEquals("cellar", grounded.scene());
    }

    private static RuntimePlan plan(String scene) {
        return new RuntimePlan(scene, "npc", "judgment", "narration", null, List.of(), List.of());
    }
}
