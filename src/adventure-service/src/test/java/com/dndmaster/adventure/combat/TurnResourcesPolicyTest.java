package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.combat.TurnResources;
import org.junit.jupiter.api.Test;

class TurnResourcesPolicyTest {
    @Test
    void reserves_without_consuming_and_commits_exactly_once() {
        TurnResources initial = TurnResources.initial();

        TurnResources.Reservation reservation = initial.reserve(TurnResourceCost.actionOnly());

        assertEquals(initial, initial.release(reservation));
        TurnResources committed = initial.commit(reservation);
        assertEquals(30, committed.movement());
        assertEquals(false, committed.actionAvailable());
        assertEquals(committed, committed.release(reservation));
    }

    @Test
    void rejects_a_cost_that_would_make_any_resource_negative() {
        TurnResources initial = new TurnResources(5, false, true, true);

        assertThrows(IllegalStateException.class,
                () -> initial.reserve(new TurnResourceCost(10, false, false, false)));
    }
}
