package com.dndmaster.combatmap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dndmaster.combatmap.application.movement.CombatMapMovementService;
import com.dndmaster.combatmap.application.movement.MovementResolutionRecoveryWorker;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class MovementResolutionRecoveryWorkerTest {
    @Test
    void scheduled_poll_resumes_durable_movement_operations() {
        CombatMapMovementService movementService = mock(CombatMapMovementService.class);

        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        new MovementResolutionRecoveryWorker(movementService, Clock.fixed(now, ZoneOffset.UTC)).process();

        verify(movementService).retryWaitingOperations();
        verify(movementService).recoverStalledOperations(now.minusSeconds(30));
    }
}
