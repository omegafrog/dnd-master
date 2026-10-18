package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.dndmaster.adventure.application.combat.*;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
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

        var retry = controller.advanceExplorationEnvironmentPhase(adventureId,
                new AdventureController.ExplorationCycleFinishRequest(adventure.version(), phaseId, false));
        assertEquals(first.cycle(), retry.cycle());
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
        when(adventures.findByIdForUpdate(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        when(players.playerId()).thenReturn(owner);
        AdventureController controller = controller(adventures, mock(RuntimeTurnApplicationService.class),
                mock(CombatMapPort.class), mock(CombatMapViewPort.class), players,
                mock(AppliedRuleSetApplicationService.class));

        controller.linkExplorationCycleToCombat(adventureId, encounterId);
        ExplorationCycle linked = storedCycle(adventure);
        assertEquals(cycle.id(), linked.id());
        assertEquals(cycle.currentPhaseCommandId(), linked.currentPhaseCommandId());
        assertEquals(0, linked.elapsedSeconds());
        assertFalse(linked.pendingEnvironmentProposal().encounterRequested());
        assertEquals(encounterId, linked.pendingEnvironmentProposal().linkedCombatEncounterId());
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
        assertTrue(retry.cycle().movementEventResolved(eventId));
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
        UUID groupId = UUID.randomUUID(), mapId = UUID.randomUUID(), enemyId = UUID.randomUUID(), phase1 = UUID.randomUUID();
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
                new ExplorationCycle.EnvironmentProposal("함정과 주변 환경이 변하지 않습니다.", List.of()),
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

        ExplorationCycle current = cycle;
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
