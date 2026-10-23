package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.combat.*;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.*;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.*;
import com.dndmaster.adventure.domain.ruleset.AppliedRuleSet;
import com.dndmaster.adventure.domain.ruleset.DndEdition;
import com.dndmaster.adventure.domain.ruleset.SelectedRulebooks;
import com.dndmaster.adventure.domain.runtime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

class ExplorationEnvironmentControllerTest {
    @Test void explorationCycleRequiresMapNeededActiveSituation() {
        var fixture = explorationCycleStartFixture(false, Optional.empty());
        Logger logger = (Logger) LoggerFactory.getLogger(AdventureController.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        ApiRequestGuard.ApiContractException failure;
        try {
            failure = assertThrows(ApiRequestGuard.ApiContractException.class,
                    () -> fixture.controller().startExplorationCycle(fixture.adventureId(),
                            new AdventureController.ExplorationCycleStartRequest(fixture.adventure().version(), UUID.randomUUID())));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }

        assertEquals("EXPLORATION_REQUIRES_MAP_NEEDED_SITUATION", failure.getMessage());
        verify(fixture.adventures(), never()).save(any());
        String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(logs.contains("outcome=started"));
        assertTrue(logs.contains("outcome=failed"));
        assertTrue(logs.contains("expectedMapNeeded=true actualMapNeeded=false"));
        assertTrue(logs.contains("recoveryAction=resolve_situation_or_end_combat"));
    }

    @Test void explorationCycleCannotStartWhileCombatIsActive() {
        var fixture = explorationCycleStartFixture(true,
                Optional.of(mock(com.dndmaster.adventure.domain.combat.CombatEncounter.class)));

        ApiRequestGuard.ApiContractException failure = assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> fixture.controller().startExplorationCycle(fixture.adventureId(),
                        new AdventureController.ExplorationCycleStartRequest(fixture.adventure().version(), UUID.randomUUID())));

        assertEquals("EXPLORATION_CYCLE_SUSPENDED_FOR_COMBAT", failure.getMessage());
        verify(fixture.adventures(), never()).save(any());
    }

    private static CycleStartFixture explorationCycleStartFixture(boolean mapNeeded,
            Optional<com.dndmaster.adventure.domain.combat.CombatEncounter> activeCombat) {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID(), packageId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party, new AdventureContext("복도", "", "", ""));
        var source = new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(
                new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(UUID.randomUUID()), 1, "page:1");
        UUID mapId = mapNeeded ? UUID.randomUUID() : null;
        var situation = new com.dndmaster.adventure.domain.scenario.SituationDefinition("opening",
                List.of(com.dndmaster.adventure.domain.scenario.StageIntent.REVELATION), List.of("truth"),
                List.of(), List.of(), List.of(), List.of(), true, true, List.of(),
                com.dndmaster.adventure.domain.scenario.SituationProvenance.SOURCE_EXTRACTED, mapId, List.of(source), mapNeeded);
        var stage = new com.dndmaster.adventure.domain.scenario.DetailedStage(packageId, 1, "stage", 1, "Find the key",
                List.of(new com.dndmaster.adventure.domain.scenario.RevelationDefinition("truth", true, List.of(source))),
                new com.dndmaster.adventure.domain.scenario.ThreatDefinition("A threat waits", List.of(source)),
                new com.dndmaster.adventure.domain.scenario.PressureDefinition("pressure", "The danger grows", List.of(source)),
                new com.dndmaster.adventure.domain.scenario.FunnelDefinition("resolution", List.of("truth"), List.of(source)),
                List.of(situation), List.of(), List.of(source));
        adventure.commitStoryRuntimeState(new OwnerPlayerId(owner), 0,
                com.dndmaster.adventure.domain.runtime.story.StoryRuntimeState.start(stage));
        UUID groupId = UUID.randomUUID();
        adventure.updateExplorationState(new OwnerPlayerId(owner), adventure.version(), new ExplorationState(List.of(
                new ExplorationGroup(groupId, UUID.randomUUID(), List.of(
                        new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30))))));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        CombatLifecycleApplicationService combatLifecycle = mock(CombatLifecycleApplicationService.class);
        when(combatLifecycle.findActiveEncounter(adventureId)).thenReturn(activeCombat);
        var stageArtifacts = mock(com.dndmaster.adventure.domain.scenario.StageArtifactRepository.class);
        when(stageArtifacts.findDetailedStage(eq(new AdventureId(adventureId)), eq(packageId), eq("stage"), eq(1L), eq(1L)))
                .thenReturn(Optional.of(stage));
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), mock(CombatMapViewPort.class), players,
                mock(AppliedRuleSetApplicationService.class), combatLifecycle);
        controller.setStageArtifactRepository(stageArtifacts);
        return new CycleStartFixture(adventureId, adventure, adventures, controller);
    }

    private record CycleStartFixture(UUID adventureId, Adventure adventure, AdventureRepository adventures,
            AdventureController controller) {}

    @Test void playerExplorationCycleJsonOmitsInternalRoutesAndMapIdentifiers() throws Exception {
        UUID groupId = UUID.randomUUID(), playerTokenId = UUID.randomUUID(), enemyTokenId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID(), hostileTokenId = UUID.randomUUID(), movementOperationId = UUID.randomUUID();
        UUID combatId = UUID.randomUUID();
        UUID characterSheetId = UUID.randomUUID();
        var group = new ExplorationGroup(groupId, playerTokenId, List.of(
                new ExplorationGroup.Member(characterSheetId, ExplorationGroup.Formation.FRONT, 30)));
        var cycle = new ExplorationCycle(UUID.randomUUID(), 1, Map.of(groupId, 13), List.of(groupId), Set.of(groupId),
                ExplorationCycle.Phase.ENVIRONMENT, ExplorationCycle.EnvironmentPhase.ENEMIES_PATROLS_AND_DETECTION,
                Set.of(), 6, new ExplorationCycle.EnvironmentProposal("안전한 상황 서술", List.of(
                        new ExplorationCycle.EnemyMove(enemyTokenId, List.of(new ExplorationCycle.Position(3, 4)))),
                        List.of(new ExplorationCycle.FeatureInteraction(featureId)), 27, false, null), Set.of(),
                new ExplorationCycle.MovementStopEvent(UUID.randomUUID(), groupId, movementOperationId,
                        "HOSTILE_OBSERVED", List.of("HOSTILE_OBSERVED"), new ExplorationCycle.Position(2, 1), hostileTokenId,
                        Set.of(characterSheetId), Set.of(characterSheetId), false),
                Set.of(), combatId);

        String json = new ObjectMapper().writeValueAsString(
                new AdventureController.ExplorationGroupsResponse(4, List.of(group), cycle));
        String environmentJson = new ObjectMapper().writeValueAsString(
                new AdventureController.ExplorationEnvironmentPhaseResponse(4, cycle, "안전한 상황 서술"));

        assertTrue(json.contains("안전한 상황 서술"));
        assertTrue(environmentJson.contains("안전한 상황 서술"));
        assertTrue(json.contains("currentPhaseCommandId"));
        for (String privateField : List.of("initiativeByGroup", "completedEnvironmentCommands", "enemyMoves", "path",
                "tokenId", "featureInteractions", "featureId", "expectedMapVersion", "movementOperationId",
                "hostileTokenId", "linkedCombatEncounterId", "resolvedMovementEventIds", "discoveredByCharacterSheetIds",
                "awareCharacterSheetIds", "enemiesAware", "threatDiscovery")) {
            assertFalse(json.contains("\"" + privateField + "\""), privateField);
            assertFalse(environmentJson.contains("\"" + privateField + "\""), privateField);
        }
        for (UUID privateId : List.of(enemyTokenId, featureId, hostileTokenId, movementOperationId, combatId)) {
            assertFalse(json.contains(privateId.toString()), privateId.toString());
            assertFalse(environmentJson.contains(privateId.toString()), privateId.toString());
        }
        assertTrue(json.contains(groupId.toString()));
        assertTrue(json.contains("\"combatLinked\":true"));
    }

    @Test void playerExplorationCycleSanitizesPublicEventLabels() throws Exception {
        UUID groupId = UUID.randomUUID();
        var event = new ExplorationCycle.MovementStopEvent(UUID.randomUUID(), groupId, UUID.randomUUID(),
                "hidden-enemy-token-identifier", List.of("HOSTILE_OBSERVED", "private-map-feature-identifier"),
                new ExplorationCycle.Position(1, 2), UUID.randomUUID());
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10), new Random(1))
                .interruptGroupTurn(groupId, event);

        String json = new ObjectMapper().writeValueAsString(
                new AdventureController.ExplorationGroupsResponse(4, List.of(), cycle));

        assertTrue(json.contains("HOSTILE_OBSERVED"));
        assertFalse(json.contains("hidden-enemy-token-identifier"));
        assertFalse(json.contains("private-map-feature-identifier"));
        assertFalse(json.contains(event.hostileTokenId().toString()));
    }

    @Test void notificationSharesPrivateAwarenessAndRetriesWithoutExposingDiscovererIds() throws Exception {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), groupId = UUID.randomUUID();
        UUID discoverer = UUID.randomUUID(), companion = UUID.randomUUID(), commandId = UUID.randomUUID(), hostileTokenId = UUID.randomUUID();
        var party = List.of(
                new AdventurePartyMember(new CharacterSheetId(discoverer), ControlMode.DIRECT, true, true, true, true, true, true),
                new AdventurePartyMember(new CharacterSheetId(companion), ControlMode.DIRECT, true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party, new AdventureContext("복도", "", "", ""));
        var group = new ExplorationGroup(groupId, UUID.randomUUID(), List.of(
                new ExplorationGroup.Member(discoverer, ExplorationGroup.Formation.FRONT, 30),
                new ExplorationGroup.Member(companion, ExplorationGroup.Formation.REAR, 25)));
        var event = new ExplorationCycle.MovementStopEvent(UUID.randomUUID(), groupId, UUID.randomUUID(), "HOSTILE_OBSERVED",
                List.of("HOSTILE_OBSERVED"), new ExplorationCycle.Position(1, 1), null,
                Set.of(discoverer), Set.of(discoverer), false);
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10)).interruptGroupTurn(groupId, event);
        adventure.updateExplorationState(new OwnerPlayerId(owner), adventure.version(), new ExplorationState(List.of(group), cycle));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        RuntimeTurnApplicationService runtime = mock(RuntimeTurnApplicationService.class);
        when(runtime.planExplorationEnvironmentPhase(any(), any(), any(), any(), any())).thenReturn(
                new ExplorationCycle.EnvironmentProposal("알림", List.of(), List.of(), -1, false, null,
                        new ExplorationCycle.ThreatDiscovery(Set.of(), false, true, false, Set.of(hostileTokenId)), Set.of()));
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(eq(adventureId), eq(owner))).thenReturn(Optional.of(new CombatMapViewPort.View(
                UUID.randomUUID(), new CombatMapViewPort.Grid(10, 10, 70, 5), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(views.gmEnvironmentView(any(), eq(owner))).thenReturn("{} GM_MAP_STATE={\"tokens\":[{\"id\":\"" + hostileTokenId + "\",\"type\":\"ENEMY\"}]}");
        AdventureController controller = controller(adventures, runtime,
                mock(CombatMapPort.class), views, players,
                mock(AppliedRuleSetApplicationService.class));
        var request = new AdventureController.ExplorationThreatNotificationRequest(adventure.version(), commandId, groupId, discoverer);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        Logger logger = (Logger) LoggerFactory.getLogger(AdventureController.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var response = controller.notifyExplorationThreat(adventureId, request);
            assertEquals(Set.of(discoverer, companion), storedCycle(adventure).pendingMovementEvent().awareCharacterSheetIds());
            assertTrue(storedCycle(adventure).pendingMovementEvent().enemiesAware());
            String publicJson = new ObjectMapper().writeValueAsString(response);
            assertFalse(publicJson.contains("discoveredByCharacterSheetIds"));
            assertFalse(publicJson.contains("awareCharacterSheetIds"));
            var retry = controller.notifyExplorationThreat(adventureId, request);
            assertEquals(response.version(), retry.version());
            verify(adventures, times(1)).save(adventure);
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
        String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(logs.contains("commandId=" + commandId));
        assertTrue(logs.contains("memberCount=2"));
        assertTrue(logs.contains("result=shared"));
        assertTrue(logs.contains("result=already_shared"));
            assertTrue(logs.contains("enemiesHeard=true"));
    }

    @Test void movementHostileObservationUsesTypedDiscoverersAndCanBeSharedPrivately() throws Exception {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), mapId = UUID.randomUUID();
        UUID discoverer = UUID.randomUUID(), companion = UUID.randomUUID(), groupId = UUID.randomUUID();
        UUID hostileId = UUID.randomUUID(), operationId = UUID.randomUUID(), commandId = UUID.randomUUID();
        var party = List.of(
                new AdventurePartyMember(new CharacterSheetId(discoverer), ControlMode.DIRECT, true, true, true, true, true, true),
                new AdventurePartyMember(new CharacterSheetId(companion), ControlMode.DIRECT, true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party, new AdventureContext("복도", "", "", ""));
        var group = new ExplorationGroup(groupId, UUID.randomUUID(), List.of(
                new ExplorationGroup.Member(discoverer, ExplorationGroup.Formation.FRONT, 30),
                new ExplorationGroup.Member(companion, ExplorationGroup.Formation.REAR, 25)));
        var initialCycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10));
        var state = new ExplorationState(List.of(group), initialCycle);
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        RuntimeTurnApplicationService runtime = mock(RuntimeTurnApplicationService.class);
        when(runtime.planExplorationEnvironmentPhase(any(), any(), any(), any(), any())).thenReturn(
                new ExplorationCycle.EnvironmentProposal("발견", List.of(), List.of(), -1, false, null,
                        new ExplorationCycle.ThreatDiscovery(Set.of(discoverer), false, false, false, Set.of(), Set.of(hostileId),
                                Map.of(hostileId, Set.of(discoverer))),
                        Set.of(discoverer)),
                new ExplorationCycle.EnvironmentProposal("알림", List.of(), List.of(), -1, false, null,
                        new ExplorationCycle.ThreatDiscovery(Set.of(), false, true, false, Set.of(hostileId)), Set.of()));
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(eq(adventureId), eq(owner))).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(10, 10, 70, 5), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(views.gmEnvironmentView(eq(mapId), eq(owner))).thenReturn("{} GM_MAP_STATE={\"tokens\":[{\"id\":\"" + hostileId + "\",\"type\":\"ENEMY\"}]}");
        AdventureController controller = controller(adventures, runtime, mock(CombatMapPort.class), views, players,
                mock(AppliedRuleSetApplicationService.class));
        var result = new CombatMapMoveResult(0, operationId, com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED,
                List.of(), List.of(), new CombatMapPreviewPosition(1, 2), List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED")
                .withHostileTokenId(hostileId);
        Logger controllerLogger = (Logger) LoggerFactory.getLogger(AdventureController.class);
        var diagnostics = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        diagnostics.start();
        controllerLogger.addAppender(diagnostics);
        String previousDiagnostics = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        com.dndmaster.adventure.domain.runtime.ExplorationCycle.MovementStopEvent event;
        try {
            event = controller.adjudicateMovementThreatDiscovery(adventure, mapId, state, group, result, UUID.randomUUID());
            var unknownHostile = result.withHostileTokenId(UUID.randomUUID());
            assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.adjudicateMovementThreatDiscovery(
                    adventure, mapId, state, group, unknownHostile, UUID.randomUUID()));
        } finally {
            controllerLogger.detachAppender(diagnostics);
            if (previousDiagnostics == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previousDiagnostics);
        }
        String diagnosticLogs = diagnostics.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(diagnosticLogs.contains("operation=exploration_movement_threat_discovery outcome=started"));
        assertTrue(diagnosticLogs.contains("eligibleHostileCount=1"));
        assertTrue(diagnosticLogs.contains("result=private_discovery_saved"));
        assertTrue(diagnosticLogs.contains("recoveryAction=retry_same_movement_operation"));
        assertTrue(diagnosticLogs.contains("failureClass=ApiContractException"));
        assertEquals(Set.of(discoverer), event.discoveredByCharacterSheetIds());
        assertEquals(Set.of(hostileId), event.threatHostileTokenIds());
        assertFalse(event.awareCharacterSheetIds().contains(companion));
        verify(runtime).planExplorationEnvironmentPhase(any(), any(), any(), any(), argThat(input ->
                input.contains("MOVEMENT_THREAT_DISCOVERY=true") && input.contains("ELIGIBLE_HOSTILE_TOKEN_IDS")
                        && input.contains(groupId.toString()) && input.contains("FRONT") && input.contains("REAR")));

        var interrupted = initialCycle.interruptGroupTurn(groupId, event);
        adventure.updateExplorationState(new OwnerPlayerId(owner), adventure.version(), state.withCycle(interrupted));
        var request = new AdventureController.ExplorationThreatNotificationRequest(adventure.version(), commandId, groupId, discoverer);
        var response = controller.notifyExplorationThreat(adventureId, request);
        assertEquals(Set.of(discoverer, companion), storedCycle(adventure).pendingMovementEvent().awareCharacterSheetIds());
        assertTrue(storedCycle(adventure).pendingMovementEvent().enemiesAware());
        String publicJson = new ObjectMapper().writeValueAsString(response);
        assertFalse(publicJson.contains("discoveredByCharacterSheetIds"));
        var retry = controller.notifyExplorationThreat(adventureId, request);
        assertEquals(response.version(), retry.version());
        verify(runtime, times(2)).planExplorationEnvironmentPhase(any(), any(), any(), any(), any());
        verify(adventures, times(1)).save(adventure);
    }

    @Test void hiddenMapFactInEnvironmentNarrationIsRejectedBeforeProposalPersistence() {
        var fixture = narrationSafetyFixture("숨겨진 함정은 4,5에 있습니다.");
        Logger logger = (Logger) LoggerFactory.getLogger(RuntimeTurnApplicationService.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            assertThrows(IllegalStateException.class, () -> fixture.controller().advanceExplorationEnvironmentPhase(
                    fixture.adventureId(), new AdventureController.ExplorationCycleFinishRequest(
                            fixture.adventure().version(), fixture.phaseId(), false)));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }

        assertNull(storedCycle(fixture.adventure()).pendingEnvironmentProposal());
        verify(fixture.adventures(), never()).save(any());
        String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(logs.contains("outcome=failed"));
        assertTrue(logs.contains("phaseId=" + fixture.phaseId()));
        assertTrue(logs.contains("narrationChars="));
        assertTrue(logs.contains("hiddenFactCount="));
        assertTrue(logs.contains("recoveryAction=discard_proposal_retry_same_phase_id"));
        assertFalse(logs.contains("숨겨진 함정은 4,5에 있습니다."));
    }

    @Test void safeEnvironmentNarrationRemainsAvailableAndCanBePersisted() {
        var fixture = narrationSafetyFixture("복도에 먼지가 쌓여 있습니다.");

        var response = fixture.controller().advanceExplorationEnvironmentPhase(fixture.adventureId(),
                new AdventureController.ExplorationCycleFinishRequest(
                        fixture.adventure().version(), fixture.phaseId(), false));

        assertEquals("복도에 먼지가 쌓여 있습니다.", response.narration());
        assertEquals(response.narration(), storedCycle(fixture.adventure()).pendingEnvironmentProposal().narration());
        verify(fixture.adventures()).save(fixture.adventure());
    }

    @Test void hiddenEnemyNameAndPositionAreRejectedBeforeEnvironmentProposalPersistence() {
        UUID documentId = UUID.randomUUID();
        var enemy = new com.dndmaster.adventure.domain.scenario.ScenarioModelElement("ambush", "combat-scenario",
                Map.of("enemyKey", "goblin-scout", "displayName", "고블린 정찰병"),
                List.of(new com.dndmaster.adventure.domain.scenario.ScenarioSourceReference(
                        new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(documentId), 1, "encounter:1")));
        var model = new com.dndmaster.adventure.domain.scenario.ScenarioModel(1, List.of(), List.of(), List.of(),
                List.of(), List.of(enemy), List.of(), List.of(), "");
        var fixture = narrationSafetyFixture("고블린 정찰병이 3,4에 숨어 있습니다.",
                "{\"tokens\":[{\"id\":\"00000000-0000-0000-0000-000000000099\",\"type\":\"ENEMY\",\"x\":3,\"y\":4,\"discovery\":\"HIDDEN\"}]}",
                model);

        assertThrows(IllegalStateException.class, () -> fixture.controller().advanceExplorationEnvironmentPhase(
                fixture.adventureId(), new AdventureController.ExplorationCycleFinishRequest(
                        fixture.adventure().version(), fixture.phaseId(), false)));

        assertNull(storedCycle(fixture.adventure()).pendingEnvironmentProposal());
        verify(fixture.adventures(), never()).save(any());
    }

    private static EnvironmentNarrationSafetyFixture narrationSafetyFixture(String narration) {
        return narrationSafetyFixture(narration, "{\"tokens\":[]}", null);
    }

    private static EnvironmentNarrationSafetyFixture narrationSafetyFixture(String narration, String gmMap,
            com.dndmaster.adventure.domain.scenario.ScenarioModel scenarioModel) {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID(), mapId = UUID.randomUUID(), packageId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        ExplorationGroup group = new ExplorationGroup(groupId, UUID.randomUUID(),
                List.of(new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30)));
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10), new Random(1))
                .finishGroupTurn(groupId, false);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(List.of(group), cycle));

        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(any())).thenReturn(Optional.of(adventure));
        RuntimeBinding binding = mock(RuntimeBinding.class);
        when(binding.scenarioPackageId()).thenReturn(packageId);
        when(binding.bindingVersion()).thenReturn(1L);
        when(binding.activeSourceContext()).thenReturn(null);
        RuntimeBindingRepository bindings = mock(RuntimeBindingRepository.class);
        when(bindings.findCurrentByAdventureId(any())).thenReturn(Optional.of(binding));
        var scenarioPackage = mock(com.dndmaster.adventure.domain.scenario.ScenarioPackage.class);
        when(scenarioPackage.mapDefinitions()).thenReturn(List.of());
        when(scenarioPackage.storyMapBindings()).thenReturn(List.of());
        when(scenarioPackage.scenarioModel()).thenReturn(scenarioModel);
        ScenarioPackageRepository packages = mock(ScenarioPackageRepository.class);
        when(packages.findById(packageId)).thenReturn(Optional.of(scenarioPackage));
        RuntimePlanningPort planning = mock(RuntimePlanningPort.class);
        when(planning.planEnvironmentPhase(any())).thenReturn(
                new ExplorationCycle.EnvironmentProposal(narration, List.of(), 0, true, null));
        RuntimeTurnApplicationService runtime = new RuntimeTurnApplicationService(adventures, bindings, packages,
                mock(RuntimeTurnRepository.class), mock(RuntimeEvidenceSearchPort.class), planning,
                new DefaultNarrationSafetyPolicy(), mock(SessionKnowledgeSetRepository.class));
        runtime.setCharacterSheetReadPort(ignored -> "현재 캐릭터 시트");

        CombatMapViewPort views = mock(CombatMapViewPort.class);
        var mapView = new CombatMapViewPort.View(mapId, new CombatMapViewPort.Grid(10, 10, 70, 5),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0);
        when(views.playerView(eq(adventureId), eq(owner))).thenReturn(Optional.of(mapView));
        when(views.preparationView(eq(adventureId), eq(owner))).thenReturn(Optional.of(mapView));
        when(views.gmEnvironmentView(eq(mapId), eq(owner))).thenReturn(gmMap);
        when(views.gmSpatialFeatures(eq(mapId), eq(owner))).thenReturn(List.of(new CombatMapViewPort.SpatialFeature(
                UUID.randomUUID(), "TRAP", List.of(new CombatMapViewPort.Position(4, 5)), "HIDDEN", "ARMED", true)));
        runtime.setCombatMapViewPort(views);
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        AdventureController controller = controller(adventures, runtime, mock(CombatMapPort.class), views, players,
                mock(AppliedRuleSetApplicationService.class));
        return new EnvironmentNarrationSafetyFixture(adventureId, adventure, adventures, controller,
                cycle.currentPhaseCommandId());
    }

    private record EnvironmentNarrationSafetyFixture(UUID adventureId, Adventure adventure,
            AdventureRepository adventures, AdventureController controller, UUID phaseId) { }

    @Test void environmentEncounterStopsAndPersistsCycleUntilCombatHandoff() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID(), mapId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        ExplorationGroup group = new ExplorationGroup(groupId, UUID.randomUUID(),
                List.of(new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30)));
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10), new Random(1))
                .finishGroupTurn(groupId, false);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(List.of(group), cycle));

        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(any())).thenReturn(Optional.of(adventure));
        RuntimeTurnApplicationService runtime = mock(RuntimeTurnApplicationService.class);
        when(runtime.planExplorationEnvironmentPhase(any(), any(), any(), any(), any())).thenReturn(
                new ExplorationCycle.EnvironmentProposal("적이 기습해 전투가 필요합니다.", List.of(), 0, true, null));
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(eq(adventureId), eq(owner))).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(10, 10, 70, 5), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(views.gmEnvironmentView(eq(mapId), eq(owner))).thenReturn("{\"tokens\":[]}\n GM_MAP_STATE={\"tokens\":[]}");
        when(views.gmEnvironmentView(eq(mapId), eq(owner))).thenReturn("{\"tokens\":[]}\n GM_MAP_STATE={\"tokens\":[]}");
        CombatMapPort mapPort = mock(CombatMapPort.class);
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        AdventureController controller = controller(adventures, runtime, mapPort, views, players,
                mock(AppliedRuleSetApplicationService.class));

        UUID phaseId = cycle.currentPhaseCommandId();
        var first = controller.advanceExplorationEnvironmentPhase(adventureId,
                new AdventureController.ExplorationCycleFinishRequest(adventure.version(), phaseId, false));
        assertTrue(first.cycle().pendingEnvironmentProposal().encounterRequested());
        assertEquals(phaseId, first.cycle().currentPhaseCommandId());
        assertEquals(0, first.cycle().elapsedSeconds());

        var retry = assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.advanceExplorationEnvironmentPhase(adventureId,
                new AdventureController.ExplorationCycleFinishRequest(adventure.version(), phaseId, false)));
        assertEquals("EXPLORATION_ENCOUNTER_RESOLUTION_PENDING", retry.getMessage());
        verify(runtime, times(1)).planExplorationEnvironmentPhase(any(), any(), any(), any(), any());
        verify(mapPort, never()).advanceDurations(any());
        verify(mapPort, never()).advanceGameTime(any());
        assertTrue(adventure.conversation().isEmpty());
    }

    @Test void committedCombatLinksToAndPreservesSuspendedExplorationCycle() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID(), encounterId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        ExplorationGroup group = new ExplorationGroup(groupId, UUID.randomUUID(),
                List.of(new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30)));
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10), new Random(1))
                .finishGroupTurn(groupId, false)
                .withEnvironmentProposal(new ExplorationCycle.EnvironmentProposal("적대적 접촉", List.of(), 0, true, null));
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(List.of(group), cycle));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(eq(adventureId), eq(owner))).thenReturn(Optional.of(new CombatMapViewPort.View(
                UUID.randomUUID(), new CombatMapViewPort.Grid(10, 10, 70, 5), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(views.transitionCombatTokens(any())).thenReturn(new CombatMapViewPort.TokenTransitionResult("APPLIED", 1, List.of()));
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), views, players,
                mock(AppliedRuleSetApplicationService.class));

        controller.linkExplorationCycleToCombat(adventureId, encounterId);
        ExplorationCycle linked = storedCycle(adventure);
        assertEquals(cycle.id(), linked.id());
        assertEquals(cycle.currentPhaseCommandId(), linked.currentPhaseCommandId());
        assertEquals(0, linked.elapsedSeconds());
        assertFalse(linked.pendingEnvironmentProposal().encounterRequested());
        assertTrue(AdventureController.ExplorationCycleView.from(linked).pendingEnvironmentProposal().combatLinked());
        controller.linkExplorationCycleToCombat(adventureId, encounterId);
        assertEquals(linked, storedCycle(adventure));
        assertThrows(ApiRequestGuard.ApiContractException.class,
                () -> controller.linkExplorationCycleToCombat(adventureId, UUID.randomUUID()));
    }

    @Test void linkedActiveCombatBlocksEnvironmentPhaseAdvance() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID(), mapId = UUID.randomUUID(), encounterId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        ExplorationGroup group = new ExplorationGroup(groupId, UUID.randomUUID(),
                List.of(new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30)));
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10), new Random(1))
                .finishGroupTurn(groupId, false)
                .withEnvironmentProposal(new ExplorationCycle.EnvironmentProposal("적대적 접촉", List.of(), 0, true, null))
                .linkCombatEncounter(encounterId);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(List.of(group), cycle));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(any())).thenReturn(Optional.of(adventure));
        var lifecycle = mock(CombatLifecycleApplicationService.class);
        var encounter = mock(com.dndmaster.adventure.domain.combat.CombatEncounter.class);
        when(encounter.encounterId()).thenReturn(encounterId);
        when(lifecycle.findActiveEncounter(adventureId)).thenReturn(Optional.of(encounter));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        RuntimeTurnApplicationService runtime = mock(RuntimeTurnApplicationService.class);
        AdventureController controller = controller(adventures, runtime, mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), players, mock(AppliedRuleSetApplicationService.class), lifecycle);

        var failure = assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.advanceExplorationEnvironmentPhase(
                adventureId, new AdventureController.ExplorationCycleFinishRequest(adventure.version(),
                        cycle.currentPhaseCommandId(), false)));
        assertEquals("EXPLORATION_CYCLE_SUSPENDED_FOR_COMBAT", failure.getMessage());
        verifyNoInteractions(runtime);
    }

    @Test void movementInterruptionCycleStaysSuspendedForCombatAndResumesAfterThatEncounterEnds() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID();
        UUID sheetA = UUID.randomUUID(), sheetB = UUID.randomUUID();
        UUID groupA = UUID.randomUUID(), groupB = UUID.randomUUID(), encounterId = UUID.randomUUID(), eventId = UUID.randomUUID();
        var party = List.of(
                new AdventurePartyMember(new CharacterSheetId(sheetA), ControlMode.DIRECT, true, true, true, true, true, true),
                new AdventurePartyMember(new CharacterSheetId(sheetB), ControlMode.DIRECT, true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        var groups = List.of(
                new ExplorationGroup(groupA, UUID.randomUUID(), List.of(
                        new ExplorationGroup.Member(sheetA, ExplorationGroup.Formation.FRONT, 30))),
                new ExplorationGroup(groupB, UUID.randomUUID(), List.of(
                        new ExplorationGroup.Member(sheetB, ExplorationGroup.Formation.FRONT, 30))));
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupA, 12, groupB, 10));
        UUID interruptedGroup = cycle.currentGroupId();
        UUID nextGroup = cycle.groupOrder().stream().filter(id -> !id.equals(interruptedGroup)).findFirst().orElseThrow();
        var event = new ExplorationCycle.MovementStopEvent(eventId, interruptedGroup, UUID.randomUUID(),
                "HOSTILE_OBSERVED", List.of("HOSTILE_OBSERVED"), new ExplorationCycle.Position(2, 1), UUID.randomUUID());
        cycle = cycle.interruptGroupTurn(interruptedGroup, event);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(groups, cycle));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        UUID mapId = UUID.randomUUID();
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(eq(adventureId), eq(owner))).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(10, 10, 70, 5), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(views.transitionCombatTokens(any())).thenReturn(new CombatMapViewPort.TokenTransitionResult("APPLIED", 1, List.of()));
        var lifecycle = mock(CombatLifecycleApplicationService.class);
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), views, players,
                mock(AppliedRuleSetApplicationService.class), lifecycle);
        Logger logger = (Logger) LoggerFactory.getLogger(AdventureController.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
            controller.linkExplorationCycleToCombat(adventureId, encounterId);
            assertEquals(encounterId, storedCycle(adventure).linkedCombatEncounterId());
            var active = mock(com.dndmaster.adventure.domain.combat.CombatEncounter.class);
            when(active.encounterId()).thenReturn(encounterId);
            when(lifecycle.findActiveEncounter(adventureId)).thenReturn(Optional.of(active));
            var blocked = assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.resolveExplorationMovementEvent(
                    adventureId, eventId, new AdventureController.ExplorationMovementEventResolveRequest(adventure.version())));
            assertEquals("EXPLORATION_CYCLE_SUSPENDED_FOR_COMBAT", blocked.getMessage());
            assertNull(storedCycle(adventure).currentGroupId());

            when(lifecycle.findActiveEncounter(adventureId)).thenReturn(Optional.empty());
            var resumed = controller.resolveExplorationMovementEvent(adventureId, eventId,
                    new AdventureController.ExplorationMovementEventResolveRequest(adventure.version()));
            assertFalse(resumed.cycle().combatLinked());
            assertEquals(nextGroup, resumed.cycle().currentGroupId());
            assertNull(resumed.cycle().pendingMovementEvent());
            String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(logs.contains("operation=exploration_combat_handoff_resume outcome=started"));
            assertTrue(logs.contains("operation=exploration_combat_handoff_resume outcome=failed"));
            assertTrue(logs.contains("operation=exploration_combat_handoff_resume outcome=completed"));
            assertTrue(logs.contains("cycleId=" + cycle.id()));
            assertTrue(logs.contains("encounterId=" + encounterId));
            assertTrue(logs.contains("recovery=retry_after_combat_ends"));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void gmPlacementUsesCurrentMapVersionAndKeepsPendingStateWhenMapStillRequiresPlacement(
            org.springframework.boot.test.system.CapturedOutput output) {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), mapId = UUID.randomUUID();
        UUID encounterId = UUID.randomUUID(), operationId = UUID.randomUUID(), groupId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID(), groupTokenId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(characterId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        var group = new ExplorationGroup(groupId, groupTokenId, List.of(
                new ExplorationGroup.Member(characterId, ExplorationGroup.Formation.FRONT, 30)));
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10)).linkCombatEncounter(encounterId);
        var transition = new CombatTokenTransition(operationId, encounterId, mapId,
                CombatTokenTransition.Direction.ENTER, 3, CombatTokenTransition.Status.PLACEMENT_REQUIRED);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(List.of(group), cycle, transition));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(adventureId, owner)).thenReturn(Optional.of(new CombatMapViewPort.View(mapId,
                new CombatMapViewPort.Grid(8, 8, 70, 5), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 9)));
        when(views.transitionCombatTokens(any())).thenReturn(
                new CombatMapViewPort.TokenTransitionResult("PLACEMENT_REQUIRED", 9, List.of()));
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), views, players, mock(AppliedRuleSetApplicationService.class));

        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        AdventureController.ExplorationGroupsResponse response;
        try {
            response = controller.submitCombatTokenPlacement(adventureId,
                    new AdventureController.CombatTokenPlacementRequest(adventure.version(), List.of(
                            new CombatTokenTransition.Placement(characterId, 2, 3))));
        } finally {
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }

        assertEquals(CombatTokenTransition.Status.PLACEMENT_REQUIRED, response.tokenTransition().status());
        var command = org.mockito.ArgumentCaptor.forClass(CombatMapViewPort.TokenTransitionCommand.class);
        verify(views).transitionCombatTokens(command.capture());
        assertEquals(operationId, command.getValue().operationId());
        assertEquals(9, command.getValue().expectedVersion());
        assertEquals(List.of(new CombatMapViewPort.TokenTransitionPlacement(characterId, 2, 3)), command.getValue().placements());
        assertEquals(CombatTokenTransition.Status.PLACEMENT_REQUIRED,
                ((ExplorationState) adventure.gameState().values().get("explorationState")).tokenTransition().status());
        assertTrue(output.getOut().contains("operation=combat_token_manual_placement outcome=started"));
        assertTrue(output.getOut().contains("operationId=" + operationId));
        assertTrue(output.getOut().contains("expectedPlacementCount=1 actualPlacementCount=1"));
        assertTrue(output.getOut().contains("result=placement_required mapVersion=9"));
    }

    @Test void gmPlacementRejectsAnotherPlayerBeforeMapAccess() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID();
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), List.of(
                        new AdventurePartyMember(new CharacterSheetId(UUID.randomUUID()), ControlMode.DIRECT,
                                true, true, true, true, true, true)),
                new AdventureContext("복도", "", "", ""));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(UUID.randomUUID());
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), views, players, mock(AppliedRuleSetApplicationService.class));

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> controller.submitCombatTokenPlacement(adventureId,
                        new AdventureController.CombatTokenPlacementRequest(adventure.version(),
                                List.of(new CombatTokenTransition.Placement(UUID.randomUUID(), 1, 1)))));
        verifyNoInteractions(views);
    }

    @Test void generatedSpatialFeatureInteractionOutsideHazardsPhaseIsRejectedBeforePersistence() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID(), mapId = UUID.randomUUID(), featureId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        ExplorationGroup group = new ExplorationGroup(groupId, UUID.randomUUID(),
                List.of(new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30)));
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10), new Random(1))
                .finishGroupTurn(groupId, false);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(List.of(group), cycle));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(any())).thenReturn(Optional.of(adventure));
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(eq(adventureId), eq(owner))).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(10, 10, 70, 5), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(views.gmEnvironmentView(eq(mapId), eq(owner))).thenReturn("{\"tokens\":[]}\n GM_MAP_STATE={\"tokens\":[]}");
        when(views.gmEnvironmentView(eq(mapId), eq(owner))).thenReturn("{\"tokens\":[]}\n GM_MAP_STATE={\"tokens\":[]}");
        RuntimeTurnApplicationService runtime = mock(RuntimeTurnApplicationService.class);
        when(runtime.planExplorationEnvironmentPhase(any(), any(), any(), any(), any())).thenReturn(
                new ExplorationCycle.EnvironmentProposal("지원되지 않는 환경 단계", List.of(),
                        List.of(new ExplorationCycle.FeatureInteraction(featureId)), 0, false, null));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        CombatMapPort mapPort = mock(CombatMapPort.class);
        AdventureController controller = controller(adventures, runtime, mapPort, views, players,
                mock(AppliedRuleSetApplicationService.class));

        var failure = assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.advanceExplorationEnvironmentPhase(
                adventureId, new AdventureController.ExplorationCycleFinishRequest(adventure.version(),
                        cycle.currentPhaseCommandId(), false)));

        assertEquals("FEATURE_INTERACTION_OUTSIDE_HAZARDS_PHASE", failure.getMessage());
        assertNull(storedCycle(adventure).pendingEnvironmentProposal());
        verify(mapPort, never()).validateSpatialFeatureInteractionForEnvironment(any(), any(), any(), anyLong(), any());
    }

    @Test void callerCannotMarkGroupTurnInterruptedWithoutMapMovementResult() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        ExplorationGroup group = new ExplorationGroup(groupId, UUID.randomUUID(),
                List.of(new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30)));
        ExplorationCycle cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(groupId, 10));
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0, new ExplorationState(List.of(group), cycle));

        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), mock(CombatMapViewPort.class), players,
                mock(AppliedRuleSetApplicationService.class));

        var failure = assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.finishExplorationGroupTurn(
                adventureId, groupId, new AdventureController.ExplorationCycleFinishRequest(adventure.version(),
                        UUID.randomUUID(), true)));

        assertEquals("MOVEMENT_INTERRUPTION_MUST_COME_FROM_MAP_RESULT", failure.getMessage());
        assertEquals(groupId, storedCycle(adventure).currentGroupId());
        assertFalse(storedCycle(adventure).groupTurnFinished(groupId));
    }

    @Test void resolvingStoredMapInterruptionResumesAtNextGroupAndIsRetrySafe() {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID();
        UUID firstSheet = UUID.randomUUID(), secondSheet = UUID.randomUUID();
        UUID firstGroup = UUID.randomUUID(), secondGroup = UUID.randomUUID(), eventId = UUID.randomUUID();
        var party = List.of(
                new AdventurePartyMember(new CharacterSheetId(firstSheet), ControlMode.DIRECT, true, true, true, true, true, true),
                new AdventurePartyMember(new CharacterSheetId(secondSheet), ControlMode.DIRECT, true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        var groups = List.of(
                new ExplorationGroup(firstGroup, UUID.randomUUID(), List.of(
                        new ExplorationGroup.Member(firstSheet, ExplorationGroup.Formation.FRONT, 30))),
                new ExplorationGroup(secondGroup, UUID.randomUUID(), List.of(
                        new ExplorationGroup.Member(secondSheet, ExplorationGroup.Formation.FRONT, 30))));
        var cycle = ExplorationCycle.start(UUID.randomUUID(), Map.of(firstGroup, 12, secondGroup, 10));
        UUID interruptedGroup = cycle.currentGroupId();
        UUID nextGroup = cycle.groupOrder().stream().filter(id -> !id.equals(interruptedGroup)).findFirst().orElseThrow();
        var event = new ExplorationCycle.MovementStopEvent(eventId, interruptedGroup, UUID.randomUUID(),
                "SPATIAL_FEATURE_DISCOVERED", List.of("TRAP_DISCOVERED:2,1"), new ExplorationCycle.Position(2, 1), null);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0,
                new ExplorationState(groups, cycle.interruptGroupTurn(interruptedGroup, event)));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), mock(CombatMapViewPort.class), players,
                mock(AppliedRuleSetApplicationService.class));

        var response = controller.resolveExplorationMovementEvent(adventureId, eventId,
                new AdventureController.ExplorationMovementEventResolveRequest(adventure.version()));
        var retry = controller.resolveExplorationMovementEvent(adventureId, eventId,
                new AdventureController.ExplorationMovementEventResolveRequest(0L));

        assertEquals(nextGroup, response.cycle().currentGroupId());
        assertEquals(nextGroup, retry.cycle().currentGroupId());
        assertNull(retry.cycle().pendingMovementEvent());
        assertEquals(response.version(), retry.version());
    }

    @Test void persistedEnvironmentProposalMovesEnemyAndAdvancesClockAndDurationsOnlyOnce() {
        Logger logger = (Logger) LoggerFactory.getLogger(AdventureController.class);
        var appender = new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        String previous = System.getProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
        System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, "true");
        try {
        UUID owner = UUID.randomUUID(), adventureId = UUID.randomUUID(), sheetId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID(), mapId = UUID.randomUUID(), enemyId = UUID.randomUUID(), featureId = UUID.randomUUID(), phase1 = UUID.randomUUID();
        var party = List.of(new AdventurePartyMember(new CharacterSheetId(sheetId), ControlMode.DIRECT,
                true, true, true, true, true, true));
        Adventure adventure = Adventure.create(new AdventureId(adventureId), SessionId.generate(), new OwnerPlayerId(owner),
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()), party,
                new AdventureContext("복도", "", "", ""));
        ExplorationGroup group = new ExplorationGroup(groupId, UUID.randomUUID(),
                List.of(new ExplorationGroup.Member(sheetId, ExplorationGroup.Formation.FRONT, 30)));
        ExplorationCycle cycle = ExplorationCycle.start(phase1, Map.of(groupId, 10), new Random(1))
                .finishGroupTurn(groupId, false);
        adventure.updateExplorationState(new OwnerPlayerId(owner), 0,
                new ExplorationState(List.of(group), cycle));

        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(any())).thenReturn(Optional.of(adventure));
        CombatMapPort mapPort = mock(CombatMapPort.class);
        AtomicLong mapVersion = new AtomicLong(4);
        when(mapPort.moveEnemyForEnvironment(any())).thenAnswer(call -> mapVersion.incrementAndGet());
        when(mapPort.interactSpatialFeatureForEnvironment(any(), any(), any(), anyLong(), any()))
                .thenAnswer(call -> new CombatMapSpatialResult(mapId, mapVersion.incrementAndGet(), List.of("TRAP_INTERACTED:2,2")));
        Map<UUID, Long> appliedDurationCommands = new HashMap<>();
        when(mapPort.advanceDurations(any())).thenAnswer(call -> {
            var command = (CombatMapSpatialTurnCommand) call.getArgument(0);
            long version = appliedDurationCommands.computeIfAbsent(command.commandId(), ignored -> mapVersion.incrementAndGet());
            return new CombatMapSpatialResult(mapId, version, List.of());
        });
        AtomicInteger gameTimeAttempts = new AtomicInteger();
        doAnswer(call -> {
            if (gameTimeAttempts.getAndIncrement() == 0) throw new IllegalStateException("simulated retryable game clock failure");
            return null;
        }).when(mapPort).advanceGameTime(any());
        CombatMapViewPort views = mock(CombatMapViewPort.class);
        when(views.playerView(eq(adventureId), eq(owner))).thenAnswer(call -> Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(10, 10, 70, 5), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), mapVersion.get())));
        when(views.gmEnvironmentView(mapId, owner)).thenReturn("{\"tokens\":[]}");
        RuntimeTurnApplicationService runtime = mock(RuntimeTurnApplicationService.class);
        when(runtime.planExplorationEnvironmentPhase(any(), any(), any(), any(), any())).thenReturn(
                new ExplorationCycle.EnvironmentProposal("순찰병이 이동합니다.", List.of(new ExplorationCycle.EnemyMove(
                        enemyId, List.of(new ExplorationCycle.Position(3, 3), new ExplorationCycle.Position(4, 3))))),
                new ExplorationCycle.EnvironmentProposal("함정이 작동합니다.", List.of(), List.of(
                        new ExplorationCycle.FeatureInteraction(featureId)), -1, false, null),
                new ExplorationCycle.EnvironmentProposal("지속시간을 진행합니다.", List.of()));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        AppliedRuleSetApplicationService rules = mock(AppliedRuleSetApplicationService.class);
        when(rules.readRuleSet(any(), any())).thenReturn(new AppliedRuleSet(
                new com.dndmaster.adventure.domain.ruleset.RuleSetId(UUID.randomUUID()),
                new DndEdition("5E"), new SelectedRulebooks(List.of(
                        new com.dndmaster.adventure.domain.ruleset.RegisteredRulebookReference(
                                new com.dndmaster.adventure.domain.ruleset.RulebookId(UUID.randomUUID()))))));
        AdventureController controller = controller(adventures, runtime, mapPort, views, players, rules);

        AdventureController.ExplorationCycleView current = AdventureController.ExplorationCycleView.from(cycle);
        AdventureController.ExplorationCycleFinishRequest retryRequest = null;
        for (int index = 0; index < 3; index++) {
            UUID commandId = current.currentPhaseCommandId();
            var request = new AdventureController.ExplorationCycleFinishRequest(adventure.version(), commandId, false);
            if (index == 2) {
                retryRequest = request;
                assertThrows(IllegalStateException.class, () -> controller.advanceExplorationEnvironmentPhase(adventureId, request));
                assertEquals(ExplorationCycle.EnvironmentPhase.DURATIONS_AND_DEADLINES,
                        storedCycle(adventure).environmentPhase());
                assertTrue(storedCycle(adventure).pendingEnvironmentProposal() != null);
                assertEquals(0, storedCycle(adventure).completedEnvironmentActions().size());
                assertEquals(1, gameTimeAttempts.get());
                retryRequest = new AdventureController.ExplorationCycleFinishRequest(adventure.version(), commandId, false);
                current = controller.advanceExplorationEnvironmentPhase(adventureId, retryRequest).cycle();
            } else {
                current = controller.advanceExplorationEnvironmentPhase(adventureId, request).cycle();
            }
        }

        assertEquals(ExplorationCycle.Phase.GROUP_TURNS, current.phase());
        assertEquals(6, current.elapsedSeconds());
        verify(runtime, times(3)).planExplorationEnvironmentPhase(any(), any(), any(), any(), any());
        verify(mapPort, times(1)).moveEnemyForEnvironment(any());
        verify(mapPort, times(1)).validateSpatialFeatureInteractionForEnvironment(eq(mapId), eq(owner), eq(featureId), eq(5L), any());
        verify(mapPort, times(1)).interactSpatialFeatureForEnvironment(eq(mapId), eq(owner), eq(featureId), anyLong(), any());
        var durationCommands = org.mockito.ArgumentCaptor.forClass(CombatMapSpatialTurnCommand.class);
        verify(mapPort, times(2)).advanceDurations(durationCommands.capture());
        assertEquals(durationCommands.getAllValues().get(0).commandId(), durationCommands.getAllValues().get(1).commandId());
        var clockCommands = org.mockito.ArgumentCaptor.forClass(CombatMapGameTimeCommand.class);
        verify(mapPort, times(2)).advanceGameTime(clockCommands.capture());
        assertEquals(clockCommands.getAllValues().get(0).commandId(), clockCommands.getAllValues().get(1).commandId());
        assertTrue(adventure.conversation().isEmpty());
        UUID completedPhaseId = retryRequest.commandId();
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.advanceExplorationEnvironmentPhase(
                adventureId, new AdventureController.ExplorationCycleFinishRequest(adventure.version(), completedPhaseId, false)));
        verify(mapPort, times(1)).moveEnemyForEnvironment(any());
        String logs = appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertTrue(logs.contains("phaseId=" + cycle.currentPhaseCommandId()));
        assertTrue(logs.contains("expectedEnemyMoves=1 actualEnemyMoves=1"));
        assertTrue(logs.contains("expectedFeatureInteractions=1"));
        assertTrue(logs.contains("actualFeatureInteractions=1"));
        assertTrue(logs.contains("elapsedSeconds=6"));
        assertTrue(logs.contains("recoveryAction=retry_same_phase_id"));
        } finally {
            logger.detachAppender(appender);
            if (previous == null) System.clearProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG);
            else System.setProperty(com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.FLAG, previous);
        }
    }

    private static ExplorationCycle storedCycle(Adventure adventure) {
        return ((ExplorationState) adventure.gameState().values().get("explorationState")).cycle();
    }

    private static AdventureController controller(AdventureRepository adventures, RuntimeTurnApplicationService runtime,
            CombatMapPort mapPort, CombatMapViewPort views, AuthenticatedPlayerResolver players,
            AppliedRuleSetApplicationService rules) {
        return controller(adventures, runtime, mapPort, views, players, rules, mock(CombatLifecycleApplicationService.class));
    }
    private static AdventureController controller(AdventureRepository adventures, RuntimeTurnApplicationService runtime,
            CombatMapPort mapPort, CombatMapViewPort views, AuthenticatedPlayerResolver players,
            AppliedRuleSetApplicationService rules, CombatLifecycleApplicationService combatLifecycle) {
        return new AdventureController(mock(com.dndmaster.adventure.application.saved.SavedAdventureApplicationService.class),
                runtime, adventures, mock(com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder.class),
                mock(com.dndmaster.adventure.application.runtime.GmTurnRepository.class),
                mock(com.dndmaster.adventure.application.runtime.RuntimeTurnRepository.class),
                mock(com.dndmaster.adventure.application.runtime.SessionEventRepository.class),
                mock(com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService.class),
                mock(com.dndmaster.adventure.application.combat.AdventureCombatApplicationService.class),
                mock(com.dndmaster.adventure.application.combat.CombatActionApplicationService.class),
                mock(com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService.class), players,
                provider(mapPort), provider(mock(SpatialActionAuthorizationPort.class)), provider(mock(CharacterCombatPort.class)),
                new ObjectMapper(), provider(views), provider(mock(CombatMapPreparationPort.class)),
                mock(PendingMapMovementConfirmationRepository.class),
                mock(com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository.class),
                combatLifecycle, rules,
                mock(com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService.class));
    }
    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            public T getObject() { return value; }
            public T getIfAvailable(java.util.function.Supplier<T> fallback) { return value; }
        };
    }
}
