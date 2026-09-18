package com.dndmaster.combatmap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.MovementResolutionRecoveryWorker;
import org.junit.jupiter.api.Test;

class MovementResolutionRecoveryWorkerTest {
    @Test
    void scheduled_poll_resumes_durable_movement_operations() {
        CombatMapMovementService movementService = mock(CombatMapMovementService.class);

        new MovementResolutionRecoveryWorker(movementService).process();

        verify(movementService).retryWaitingOperations();
    }
}
