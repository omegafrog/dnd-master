package com.dndmaster.adventure.api;

import static org.mockito.Mockito.*;

import com.dndmaster.adventure.api.AdventureSessionController;
import com.dndmaster.adventure.api.AuthenticatedPlayerResolver;
import com.dndmaster.adventure.application.runtime.TacticalMapActivationApplicationService;
import com.dndmaster.adventure.application.runtime.TacticalTriggerRuntimeApplicationService;
import com.dndmaster.adventure.application.runtime.GmProviderBindingService;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdventureSessionControllerTacticalStartTest {
    @Test
    void normalAdventureStartBindsTheActivatedCurrentStageMapForTriggers() {
        var owner = UUID.randomUUID();
        var adventureId = UUID.randomUUID();
        var combatMapId = UUID.randomUUID();
        var sessionId = new SessionId(UUID.randomUUID());
        var packageId = UUID.randomUUID();
        var ruleSetId = UUID.randomUUID();
        var mapDefinitionId = UUID.randomUUID();
        var session = mock(AdventureSession.class);
        when(session.id()).thenReturn(sessionId);
        when(session.startedAdventureId()).thenReturn(new AdventureId(adventureId));
        when(session.scenarioPackageId()).thenReturn(packageId);
        var runtime = mock(AdventureSessionRuntimeConfiguration.class);
        when(runtime.ruleSetId()).thenReturn(new RuleSetId(ruleSetId));
        when(session.runtimeConfiguration()).thenReturn(runtime);
        var stage = mock(AdventureStoryPlanStage.class);
        when(stage.position()).thenReturn(1);
        when(stage.mapDefinitionId()).thenReturn(mapDefinitionId);
        when(stage.playerSpawnX()).thenReturn(2);
        when(stage.playerSpawnY()).thenReturn(3);
        var scene = mock(TacticalScenePlan.class);
        when(scene.readyForActivation()).thenReturn(true);
        when(stage.tacticalScenePlan()).thenReturn(scene);
        var plan = mock(AdventureStoryPlan.class);
        when(plan.currentStage()).thenReturn(0);
        when(plan.stages()).thenReturn(List.of(stage));

        var sessions = mock(com.dndmaster.adventure.application.session.AdventureSessionApplicationService.class);
        var stories = mock(AdventureStoryPlanApplicationService.class);
        var activation = mock(TacticalMapActivationApplicationService.class);
        var triggers = mock(TacticalTriggerRuntimeApplicationService.class);
        var resolver = mock(AuthenticatedPlayerResolver.class);
        when(resolver.playerId()).thenReturn(owner);
        when(sessions.start(any(), any(), anyLong(), any(), any())).thenReturn(session);
        when(stories.read(sessionId, new OwnerPlayerId(owner))).thenReturn(plan);
        when(activation.activateDefinition(packageId, adventureId, owner, ruleSetId, mapDefinitionId, scene, 2, 3))
                .thenReturn(new TacticalMapActivationApplicationService.Activation(Optional.of(combatMapId), false));

        var controller = new AdventureSessionController(sessions, resolver, mock(GmProviderBindingService.class), stories, activation, triggers);
        controller.start(sessionId.value(), 0, UUID.randomUUID(), new AdventureSessionController.StartRequest(adventureId));

        verify(triggers).bindActiveMap(adventureId, 1, owner, combatMapId);
    }
}
