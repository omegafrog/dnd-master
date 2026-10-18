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
        when(runtime.planExplorationEnvironmentPhase(any(), any(), any(), any())).thenReturn(
                new ExplorationCycle.EnvironmentProposal("적과 환경을 판단합니다.", List.of()),
                new ExplorationCycle.EnvironmentProposal("함정과 주변 환경이 변하지 않습니다.", List.of()),
                new ExplorationCycle.EnvironmentProposal("순찰병이 이동합니다.", List.of(new ExplorationCycle.EnemyMove(
                        enemyId, List.of(new ExplorationCycle.Position(3, 3), new ExplorationCycle.Position(4, 3))))));
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
                assertEquals(1, storedCycle(adventure).completedEnvironmentActions().size());
                assertEquals(1, gameTimeAttempts.get());
                retryRequest = new AdventureController.ExplorationCycleFinishRequest(adventure.version(), commandId, false);
                current = controller.advanceExplorationEnvironmentPhase(adventureId, retryRequest).cycle();
            } else {
                current = controller.advanceExplorationEnvironmentPhase(adventureId, request).cycle();
            }
        }

        assertEquals(ExplorationCycle.Phase.GROUP_TURNS, current.phase());
        assertEquals(6, current.elapsedSeconds());
        verify(runtime, times(3)).planExplorationEnvironmentPhase(any(), any(), any(), any());
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
                mock(CombatLifecycleApplicationService.class), rules,
                mock(com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService.class));
    }
    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            public T getObject() { return value; }
            public T getIfAvailable(java.util.function.Supplier<T> fallback) { return value; }
        };
    }
}
