package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.combat.CombatMovementPolicy;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.combat.TurnResources;
import org.junit.jupiter.api.Test;

class CombatMovementPolicyTest {
    @Test
    void movement_can_be_split_and_consumes_only_the_distance_taken() {
        TurnResources initial = TurnResources.initial();

        TurnResources afterFirstMove = initial.commit(
                initial.reserve(TurnResourceCost.movementOnly(10)));
        TurnResources afterSecondMove = afterFirstMove.commit(
                afterFirstMove.reserve(TurnResourceCost.movementOnly(15)));

        assertEquals(5, afterSecondMove.movement());
        assertEquals(true, afterSecondMove.actionAvailable());
    }

    @Test
    void rejects_a_move_that_exceeds_the_remaining_allowance() {
        TurnResources resources = new TurnResources(10, true, true, true);

        assertThrows(IllegalStateException.class,
                () -> resources.reserve(TurnResourceCost.movementOnly(15)));
    }

    @Test
    void calculates_grid_distance_from_a_path() {
        assertEquals(10, CombatMovementPolicy.distanceOf("0,0;1,0;2,0"));
    }
}
