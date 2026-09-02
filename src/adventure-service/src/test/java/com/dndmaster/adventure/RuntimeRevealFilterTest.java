package com.dndmaster.adventure;

import com.dndmaster.adventure.application.runtime.CheckSelection;
import com.dndmaster.adventure.application.runtime.DeterministicRevealFilter;
import com.dndmaster.adventure.application.runtime.ResolutionResult;
import com.dndmaster.adventure.application.runtime.TriggerDetection;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.WorldFact;
import com.dndmaster.adventure.domain.scenario.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRevealFilterTest {
    private static final String PLAYER = "player";

    @Test
    void missingCandidateIsNoCheck() {
        assertThat(CheckSelection.from(TriggerDetection.none()).decision())
                .isEqualTo(CheckSelection.Decision.NO_CHECK);
    }

    @Test
    void worldEventContractSelectsSystemOwnedCheck() {
        ScenarioResolutionUnit unit = unit(ScenarioResolutionDetail.RevealCondition.ON_SUCCESS);
        var trigger = new TriggerDetection(ScenarioResolutionDetail.TriggerType.WORLD_EVENT, "enter hall", unit);

        assertThat(CheckSelection.from(trigger).decision()).isEqualTo(CheckSelection.Decision.SYSTEM_ROLL);
    }

    @Test
    void successfulCheckRevealsOnlyContractedFactAndFailureRevealsNothing() {
        ScenarioResolutionUnit unit = unit(ScenarioResolutionDetail.RevealCondition.ON_SUCCESS);
        NarrativeState state = NarrativeState.empty().addWorldFact(new WorldFact("secret-door", "A draft comes from the wall", false));
        ResolutionResult success = new ResolutionResult(unit, 18, true);
        var visible = new DeterministicRevealFilter().reveal(state, success, PLAYER, 3, "hall", "ordinary prose");

        assertThat(visible.visibleFacts()).containsExactly("A draft comes from the wall");
        assertThat(visible.stateDelta().revealedFactIds()).containsExactly("secret-door");
        assertThat(visible.stateDelta().knowledgeChanges()).singleElement()
                .satisfies(change -> assertThat(change.factIds()).containsExactly("secret-door"));

        var failed = new DeterministicRevealFilter().reveal(state, new ResolutionResult(unit, 2, false), PLAYER, 3, "hall", "ordinary prose");
        assertThat(failed.visibleFacts()).isEmpty();
        assertThat(failed.stateDelta().revealedFactIds()).isEmpty();
    }

    private static ScenarioResolutionUnit unit(ScenarioResolutionDetail.RevealCondition condition) {
        var detail = new ScenarioResolutionDetail(
                new ScenarioResolutionDetail.TriggerContract(ScenarioResolutionDetail.TriggerType.WORLD_EVENT, "enter hall"),
                new ScenarioResolutionDetail.CheckContract(ScenarioResolutionDetail.RollMethod.SYSTEM, "perception"),
                new ScenarioResolutionDetail.StateEffect("door", "noticed", "unchanged"),
                new ScenarioResolutionDetail.RevealContract(condition, ScenarioResolutionDetail.RevealLevel.CLUE, "secret-door"),
                new ScenarioResolutionDetail.PriorKnowledge(false, List.of()), null, null, null, null, null,
                List.of(), null, null, List.of(), List.of(), List.of(), null);
        return new ScenarioResolutionUnit(ResolutionKind.SKILL_ABILITY_CHECK, "perception", 15, "1d20",
                ResolutionVisibility.GM_REFERENCE, "enter hall", List.of(new ScenarioSourceReference(
                        new KnowledgeDocumentId(UUID.randomUUID()), 1, "p1")), "test", detail,
                ResolutionStatus.COMPLETE, List.of());
    }
}
