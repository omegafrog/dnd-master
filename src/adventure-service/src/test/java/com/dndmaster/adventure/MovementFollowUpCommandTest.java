package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovementFollowUpCommandTest {
    @Test
    void requiresHostileTokenAndTurnForHostileObservation() {
        UUID operationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();

        assertThrows(NullPointerException.class,
                () -> MovementFollowUpCommand.hostileObserved(operationId, turnId, null));
        assertThrows(NullPointerException.class,
                () -> MovementFollowUpCommand.hostileObserved(operationId, null, hostileTokenId));
        assertThrows(NullPointerException.class,
                () -> new MovementFollowUpCommand(UUID.randomUUID(), operationId, hostileTokenId, null,
                        MovementFollowUpCommand.Kind.COMBAT, "HOSTILE_OBSERVED"));
    }
}
