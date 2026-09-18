package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/** Periodically resumes durable movement work from its persisted state. */
public final class MovementResolutionRecoveryWorker {
    private final CombatMapMovementService movementService;

    public MovementResolutionRecoveryWorker(CombatMapMovementService movementService) {
        this.movementService = Objects.requireNonNull(movementService);
    }

    @Scheduled(fixedDelayString = "${combat-map.movement-recovery.poll-delay-ms:1000}")
    public void process() {
        movementService.retryWaitingOperations();
    }
}
