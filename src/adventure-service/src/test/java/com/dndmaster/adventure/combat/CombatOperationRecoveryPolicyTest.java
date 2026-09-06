package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionStep;
import com.dndmaster.adventure.application.combat.CombatOperationRecoveryPolicy;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatOperationRecoveryPolicyTest {
    @Test
    void manual_retry_reuses_same_operation_command_and_reservation() {
        UUID commandId = UUID.randomUUID();
        CombatActionOperation operation = new CombatActionOperation(commandId, "fingerprint",
                UUID.randomUUID(), UUID.randomUUID(), TurnResourceCost.actionOnly(),
                List.of(new CombatActionStep("dice", commandId + ":dice", CombatActionStep.Status.DONE)));
        operation.failed(new RuntimeException("provider timeout"));

        var reservation = CombatOperationRecoveryPolicy.reservationFor(operation);

        assertSame(operation, CombatOperationRecoveryPolicy.resume(operation));
        assertEquals(operation.commandId(), commandId);
        assertEquals(operation.reservedCost(), reservation.cost());
        assertEquals(CombatActionOperation.Status.PROCESSING_FAILED, operation.status());
    }
}
