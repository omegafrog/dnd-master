package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.combat.TurnResources;
import org.junit.jupiter.api.Test;

class TurnResourcesPolicyTest {
    @Test
    void reserves_without_consuming_and_commits_exactly_once() {
        TurnResources initial = TurnResources.initial(25);

        TurnResources.Reservation reservation = initial.reserve(TurnResourceCost.actionOnly());

        assertEquals(initial, initial.release(reservation));
        TurnResources committed = initial.commit(reservation);
        assertEquals(25, committed.movement());
        assertEquals(25, committed.movementSpeed());
        assertEquals(false, committed.actionAvailable());
        assertEquals(committed, committed.release(reservation));
    }

    @Test
    void movement_spent_and_actions_are_tracked_independently_and_dash_adds_current_speed() {
        TurnResources initial = TurnResources.initial(25);
        TurnResources moved = initial.commit(initial.reserve(TurnResourceCost.movementOnly(10)));
        TurnResources acted = moved.commit(moved.reserve(TurnResourceCost.actionOnly()));
        TurnResources dashed = acted.dash();

        assertEquals(10, moved.movementUsed());
        assertEquals(15, moved.movement());
        assertEquals(25, moved.movementSpeed());
        assertEquals(15, acted.movement());
        assertEquals(40, dashed.movement());
        assertEquals(false, dashed.actionAvailable());
    }

    @Test
    void speed_change_preserves_distance_already_traveled() {
        TurnResources moved = TurnResources.initial(30)
                .commit(TurnResources.initial(30).reserve(TurnResourceCost.movementOnly(10)));

        TurnResources slowed = moved.withMovementSpeed(20);

        assertEquals(10, slowed.movementUsed());
        assertEquals(10, slowed.movement());
        assertEquals(20, slowed.movementSpeed());
    }

    @Test
    void rejects_a_cost_that_would_make_any_resource_negative() {
        TurnResources initial = new TurnResources(5, false, true, true);

        assertThrows(IllegalStateException.class,
                () -> initial.reserve(new TurnResourceCost(10, false, false, false)));
    }
}
