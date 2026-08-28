package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.TacticalPreparationState;
import com.dndmaster.adventure.application.runtime.TacticalPreparationStatePolicy;
import com.dndmaster.adventure.application.runtime.TacticalScenePreparationJobRepository;
import com.dndmaster.adventure.domain.adventure.TacticalPreparationRequirement;
import com.dndmaster.adventure.domain.adventure.FogPlan;
import com.dndmaster.adventure.domain.adventure.NormalizedCoordinate;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.TacticalPlacement;
import com.dndmaster.adventure.domain.adventure.TacticalPlacementKind;
import com.dndmaster.adventure.domain.adventure.TacticalSceneBoundary;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlanStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TacticalPreparationStatePolicyTest {
    private final TacticalPreparationStatePolicy policy = new TacticalPreparationStatePolicy();

    @Test
    void distinguishes_absent_future_and_current_preparation_states() {
        assertEquals(TacticalPreparationState.NOT_REQUIRED,
                policy.compose(TacticalPreparationRequirement.NOT_REQUIRED, false, Optional.empty(), TacticalScenePlan.absent()));
        assertEquals(TacticalPreparationState.REQUIRED_PENDING,
                policy.compose(TacticalPreparationRequirement.REQUIRED, false, Optional.empty(), TacticalScenePlan.absent()));
        assertEquals(TacticalPreparationState.PREPARING,
                policy.compose(TacticalPreparationRequirement.REQUIRED, true,
                        Optional.of(job(TacticalScenePreparationJobRepository.Status.RUNNING)), TacticalScenePlan.absent()));
        assertEquals(TacticalPreparationState.READY,
                policy.compose(TacticalPreparationRequirement.REQUIRED, true,
                        Optional.of(job(TacticalScenePreparationJobRepository.Status.COMPLETE)), readyScene()));
        assertEquals(TacticalPreparationState.FAILED_RETRYABLE,
                policy.compose(TacticalPreparationRequirement.REQUIRED, true,
                        Optional.of(job(TacticalScenePreparationJobRepository.Status.FAILED_RETRYABLE)), TacticalScenePlan.absent()));
    }

    private static TacticalScenePreparationJobRepository.Job job(TacticalScenePreparationJobRepository.Status status) {
        return new TacticalScenePreparationJobRepository.Job(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "stage", status, 0, 0, true, "message", null, java.time.Instant.now());
    }

    private static TacticalScenePlan readyScene() {
        var grounding = PlacementGrounding.aiInference("bounded player placement");
        return new TacticalScenePlan(TacticalScenePlan.CURRENT_SCHEMA_VERSION, TacticalScenePlanStatus.READY,
                new TacticalSceneBoundary(new NormalizedCoordinate(0, 0), new NormalizedCoordinate(1, 1), List.of()),
                List.of(new TacticalPlacement("player", TacticalPlacementKind.PLAYER, new NormalizedCoordinate(.1, .1), grounding)),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), new FogPlan(List.of(), grounding),
                List.of(), List.of(), List.of());
    }
}
