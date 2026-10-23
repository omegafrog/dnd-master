package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.domain.combat.CombatMovementPolicy;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import com.dndmaster.adventure.domain.combat.TurnResources;
import org.junit.jupiter.api.Test;

class CombatMovementPolicyTest {
    @Test
    void movement_can_be_used_before_between_and_after_actions_with_only_actual_distance_spent() {
        TurnResources initial = TurnResources.initial(30);

        TurnResources beforeAction = initial.commit(
                initial.reserve(TurnResourceCost.movementOnly(10)));
        TurnResources afterAction = beforeAction.commit(
                beforeAction.reserve(TurnResourceCost.actionOnly()));
        TurnResources betweenActions = afterAction.commit(
                afterAction.reserve(TurnResourceCost.movementOnly(5)));
        TurnResources afterBonusAction = betweenActions.commit(
                betweenActions.reserve(new TurnResourceCost(0, false, true, false)));
        TurnResources afterSecondMove = afterBonusAction.commit(
                afterBonusAction.reserve(TurnResourceCost.movementOnly(15)));

        assertEquals(0, afterSecondMove.movement());
        assertEquals(30, afterSecondMove.movementSpeed());
        assertEquals(30, afterSecondMove.movementUsed());
        assertEquals(false, afterSecondMove.actionAvailable());
        assertEquals(false, afterSecondMove.bonusActionAvailable());
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
