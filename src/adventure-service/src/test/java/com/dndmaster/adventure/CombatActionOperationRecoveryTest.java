package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActionStep;
import com.dndmaster.adventure.domain.combat.TurnResourceCost;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatActionOperationRecoveryTest {
    @Test
    void restores_the_server_issued_map_operation_id_for_retry_independently_of_client_command_id() {
        UUID clientCommandId = UUID.randomUUID();
        UUID serverMapOperationId = UUID.randomUUID();
        CombatActionOperation saved = CombatActionOperation.restore(
                clientCommandId,
                "request-fingerprint",
                UUID.randomUUID(),
                UUID.randomUUID(),
                new TurnResourceCost(0, false, false, false),
                List.of(new CombatActionStep("map", serverMapOperationId.toString(), CombatActionStep.Status.DONE)),
                CombatActionOperation.Status.PROCESSING_FAILED,
                new CombatActionResponse(UUID.randomUUID(), UUID.randomUUID(), 3, "COMMITTED", null, null, List.of()),
                "follow-up unavailable");

        CombatActionOperation recovered = CombatActionOperation.restore(
                saved.commandId(), saved.fingerprint(), saved.encounterId(), saved.actorId(), saved.reservedCost(),
                saved.steps(), saved.status(), saved.response(), saved.failure());

        assertEquals(serverMapOperationId, recovered.mapOperationId());
        assertNotEquals(clientCommandId, recovered.mapOperationId());
        assertEquals(CombatActionOperation.Status.PROCESSING_FAILED, recovered.status());
        assertEquals(saved.response(), recovered.response());
    }
}
