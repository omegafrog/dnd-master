package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.runtime.clock.AdventureClock;
import com.dndmaster.adventure.domain.runtime.clock.GameDuration;
import com.dndmaster.adventure.domain.runtime.clock.GameTimePolicy;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFact;
import com.dndmaster.adventure.domain.runtime.fact.CommittedWorldFactLedger;
import com.dndmaster.adventure.domain.runtime.fact.FactVisibility;
import com.dndmaster.adventure.domain.runtime.plan.AdventureStoryPlanRevision;
import com.dndmaster.adventure.domain.runtime.plan.StoryPlanRevisionValidator;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoryContinuityPolicyTest {
    @Test
    void committed_public_fact_cannot_be_contradicted_but_hidden_branch_can_change() {
        UUID turn = UUID.randomUUID();
        var ledger = CommittedWorldFactLedger.empty().append(new CommittedWorldFact(
                UUID.randomUUID(), "gate", "state", "open", FactVisibility.PUBLIC,
                "gm-turn", turn, 1));
        var current = AdventureStoryPlanRevision.initial(UUID.randomUUID(), List.of("find gate"), turn);
        var validator = new StoryPlanRevisionValidator();

        assertThrows(IllegalArgumentException.class, () -> validator.revise(current,
                List.of("gate state closed"), ledger, List.of(), List.of(), UUID.randomUUID()));
        var revised = validator.revise(current, List.of("find another route"), ledger,
                List.of(), List.of(), UUID.randomUUID());
        assertEquals(current.version() + 1, revised.version());
        assertEquals(current.revisionId(), revised.predecessorRevisionId());
    }

    @Test
    void plan_history_and_clock_are_immutable_and_clock_does_not_follow_response_count() {
        UUID session = UUID.randomUUID();
        var clock = AdventureClock.initial(session);
        var advanced = clock.advance(GameDuration.turns(5), UUID.randomUUID());
        assertEquals(0, clock.turnsElapsed());
        assertEquals(5, advanced.turnsElapsed());
        assertEquals(advanced, advanced.advance(GameDuration.turns(5), advanced.lastCauseTurnId()));
        assertThrows(IllegalArgumentException.class, () -> advanced.advance(GameDuration.seconds(-1), UUID.randomUUID()));
        assertEquals(5, advanced.turnsElapsed());
    }

    @Test
    void rule_time_wins_and_missing_rule_uses_five_turns_per_minute() {
        assertEquals(30, GameTimePolicy.durationForTurns(3, OptionalInt.of(10)).seconds());
        assertEquals(60, GameTimePolicy.durationForTurns(5, OptionalInt.empty()).seconds());
    }
}
