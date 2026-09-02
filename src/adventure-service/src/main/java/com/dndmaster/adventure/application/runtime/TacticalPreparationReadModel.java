package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Internal composition used to derive a player-safe projection and operator diagnostics. */
public record TacticalPreparationReadModel(UUID sessionId, int stagePosition, String stageName,
        TacticalPreparationRequirement requirement, boolean currentStage, TacticalPreparationState state,
        Optional<TacticalScenePreparationJobRepository.Job> job, TacticalScenePlan scene,
        PlayerSafeProjection player, InternalDiagnostics diagnostics) {
    public TacticalPreparationReadModel {
        Objects.requireNonNull(sessionId, "session id must not be null");
        if (stagePosition < 1) throw new IllegalArgumentException("stage position must be positive");
        Objects.requireNonNull(stageName, "stage name must not be null");
        Objects.requireNonNull(requirement, "requirement must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(job, "job must not be null");
        Objects.requireNonNull(scene, "scene must not be null");
        Objects.requireNonNull(player, "player projection must not be null");
        Objects.requireNonNull(diagnostics, "diagnostics must not be null");
    }

    public record PlayerSafeProjection(TacticalPreparationState state, String message, int progress, int attempts,
            boolean mapRequired, boolean mapActivationAllowed, Instant updatedAt, PreparationProgress preparationProgress) {
        public PlayerSafeProjection(TacticalPreparationState state, String message, int progress, int attempts,
                boolean mapRequired, boolean mapActivationAllowed, Instant updatedAt) {
            this(state, message, progress, attempts, mapRequired, mapActivationAllowed, updatedAt,
                    PreparationProgress.legacy(progress));
        }
    }

    public record InternalDiagnostics(String jobStatus, String failureReason, String sceneStatus,
            boolean mapActivationAllowed, Instant updatedAt) {}
}
