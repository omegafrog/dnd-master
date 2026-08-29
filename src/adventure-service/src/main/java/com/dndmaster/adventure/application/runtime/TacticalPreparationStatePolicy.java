package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.Objects;
import java.util.Optional;

/** Composes intent and execution state without treating an absent future scene as unnecessary. */
public final class TacticalPreparationStatePolicy {
    public TacticalPreparationState compose(TacticalPreparationRequirement requirement, boolean currentStage,
            Optional<TacticalScenePreparationJobRepository.Job> job, TacticalScenePlan scene) {
        Objects.requireNonNull(requirement, "tactical preparation requirement must not be null");
        Objects.requireNonNull(job, "tactical preparation job must not be null");
        Objects.requireNonNull(scene, "tactical scene snapshot must not be null");
        if (requirement == TacticalPreparationRequirement.NOT_REQUIRED) return TacticalPreparationState.NOT_REQUIRED;
        if (!currentStage) return TacticalPreparationState.REQUIRED_PENDING;
        if (job.map(value -> value.status() == TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE).orElse(false)) {
            return TacticalPreparationState.FAILED_RETRYABLE;
        }
        if (scene.readyForActivation()
                && job.map(value -> value.status() == TacticalScenePreparationJobRepository.Status.COMPLETE).orElse(true)) {
            return TacticalPreparationState.READY;
        }
        if (job.map(value -> value.status() == TacticalScenePreparationJobRepository.Status.QUEUED
                || value.status() == TacticalScenePreparationJobRepository.Status.RUNNING).orElse(false)) {
            return TacticalPreparationState.PREPARING;
        }
        return TacticalPreparationState.REQUIRED_PENDING;
    }
}
