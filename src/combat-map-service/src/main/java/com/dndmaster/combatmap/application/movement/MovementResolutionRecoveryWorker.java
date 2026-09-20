package com.dndmaster.combatmap.application.movement;

import java.util.Objects;
import java.time.Clock;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;

/** Periodically resumes durable movement work from its persisted state. */
public final class MovementResolutionRecoveryWorker {
    private static final Duration STALLED_AFTER = Duration.ofSeconds(30);
    private final CombatMapMovementService movementService;
    private final Clock clock;

    public MovementResolutionRecoveryWorker(CombatMapMovementService movementService) {
        this(movementService, Clock.systemUTC());
    }

    public MovementResolutionRecoveryWorker(CombatMapMovementService movementService, Clock clock) {
        this.movementService = Objects.requireNonNull(movementService);
        this.clock = Objects.requireNonNull(clock);
    }

    @Scheduled(fixedDelayString = "${combat-map.movement-recovery.poll-delay-ms:1000}")
    public void process() {
        movementService.retryWaitingOperations();
        movementService.recoverStalledOperations(clock.instant().minus(STALLED_AFTER));
    }
}
