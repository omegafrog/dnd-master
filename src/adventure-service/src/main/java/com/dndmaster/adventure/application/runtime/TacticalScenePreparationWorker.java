package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;

/** Claims durable tactical preparation jobs outside the player request thread. */
public final class TacticalScenePreparationWorker {
    private final TacticalScenePreparationApplicationService preparation;
    public TacticalScenePreparationWorker(TacticalScenePreparationApplicationService preparation) {
        this.preparation = Objects.requireNonNull(preparation, "preparation service must not be null");
    }
    @Scheduled(fixedDelayString = "${adventure.tactical-preparation.poll-delay-ms:1000}")
    public void processQueuedJobs() { preparation.processQueuedJobs(); }
}
