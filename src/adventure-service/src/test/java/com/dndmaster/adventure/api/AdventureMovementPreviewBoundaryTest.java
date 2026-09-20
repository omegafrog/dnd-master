package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatMapPreviewPosition;
import com.dndmaster.adventure.application.combat.CombatMapPreviewResult;
import com.dndmaster.adventure.application.combat.CombatMapPreparationPort;
import com.dndmaster.adventure.application.combat.CombatMapViewPort;
import com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService;
import com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.InvocationTargetException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AdventureMovementPreviewBoundaryTest {
    @Test
    void rejects_a_preview_for_a_map_not_owned_by_the_requested_adventure() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID adventureMapId = UUID.randomUUID();
        UUID requestedMapId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class);
        CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        Adventure adventure = mock(Adventure.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.id()).thenReturn(new AdventureId(adventureId));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                adventureMapId, new CombatMapViewPort.Grid(2, 2, 50, 5), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(combatMap.preview(any())).thenReturn(new CombatMapPreviewResult(adventureMapId,
                List.of(new CombatMapPreviewPosition(0, 0)), 0, 0, "preview"));

        AdventureController controller = controller(adventures, combatMap, mapViews, playerResolver);

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(requestedMapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(1, 1), List.of())));
        verifyNoInteractions(combatMap);
    }

    @Test
    void ambiguous_natural_language_destination_does_not_change_map_or_save_confirmation() {
        UUID adventureId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID(); UUID mapId = UUID.randomUUID(); UUID tokenId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class); CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class); AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        var pending = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class); Adventure adventure = mock(Adventure.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure)); when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(players.playerId()).thenReturn(ownerId); when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(3, 3, 50, 5), List.of(new CombatMapViewPort.Token(tokenId, "PLAYER", 0, 0)), List.of(), List.of(), List.of(), List.of(), List.of(), 4)));
        AdventureController controller = controller(adventures, combatMap, mapViews, players, pending, mock(AppliedRuleSetApplicationService.class));
        controller.setMovementPlacementModelPort(context -> new com.dndmaster.adventure.application.combat.MovementPlacementModelPort.MovementPlacementProposal(
                "AMBIGUOUS", null, List.of(), "어느 문인지 알려주세요."));

        var response = controller.previewNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementPreviewRequest(mapId, 4L, tokenId, "문으로 가", ""));

        assertEquals("AMBIGUOUS", response.status());
        verifyNoInteractions(combatMap);
        verifyNoInteractions(pending);
    }

    @Test
    void resolved_natural_language_preview_persists_the_server_route_and_confirmation_identity() {
        UUID adventureId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID(); UUID mapId = UUID.randomUUID(); UUID tokenId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class); CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class); AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        RuntimeTurnRepository runtimeTurns = mock(RuntimeTurnRepository.class);
        var pending = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class);
        var ruleSetService = mock(AppliedRuleSetApplicationService.class); Adventure adventure = mock(Adventure.class);
        var appliedRuleSet = mock(com.dndmaster.adventure.domain.ruleset.AppliedRuleSet.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.id()).thenReturn(new AdventureId(adventureId));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        UUID sessionId = UUID.randomUUID();
        var turn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        when(turn.adventureId()).thenReturn(new AdventureId(adventureId)); when(turn.sessionId()).thenReturn(sessionId);
        when(turn.turnId()).thenReturn(UUID.randomUUID()); when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.RESOLVING);
        when(turn.origin()).thenReturn(RuntimeTurnOrigin.PLAYER); when(turn.playerOrigin()).thenReturn(true);
        when(turn.gmOnly()).thenReturn(false); when(turn.agentOrigin()).thenReturn(false);
        when(adventure.sessionId()).thenReturn(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId));
        when(runtimeTurns.findAllByAdventureId(new AdventureId(adventureId))).thenReturn(List.of(turn));
        when(adventure.ruleSetId()).thenReturn(new com.dndmaster.adventure.domain.adventure.RuleSetId(UUID.randomUUID()));
        when(players.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(3, 3, 50, 5), List.of(new CombatMapViewPort.Token(tokenId, "PLAYER", 0, 0)),
                List.of(), List.of(), List.of(), List.of(), List.of(), 4)));
        when(ruleSetService.readRuleSet(any(), any())).thenReturn(appliedRuleSet);
        when(appliedRuleSet.edition()).thenReturn(new com.dndmaster.adventure.domain.ruleset.DndEdition("DND_5E_2024"));
        when(combatMap.preview(any())).thenReturn(new CombatMapPreviewResult(mapId,
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0), new CombatMapPreviewPosition(1, 1)),
                10, 4, "natural-preview"));

        AdventureController controller = controller(adventures, combatMap, mapViews, players, pending, ruleSetService, runtimeTurns);
        controller.setMovementPlacementModelPort(context -> new com.dndmaster.adventure.application.combat.MovementPlacementModelPort.MovementPlacementProposal(
                "RESOLVED", new com.dndmaster.adventure.application.combat.MovementPlacementModelPort.Position(1, 1), List.of(), "목적지를 찾았습니다."));

        controller.previewNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementPreviewRequest(mapId, 4L, tokenId, "오른쪽 문으로 가", ""));

        verify(pending).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.path().equals(List.of(new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(0, 0),
                        new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 0),
                        new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 1)))
                        && saved.distance() == 10 && saved.pendingTurnId() != null
                        && saved.destination().equals(new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 1))));
    }

    @Test
    void confirmation_keeps_active_pending_state_and_returns_the_shared_movement_result() {
        UUID adventureId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID(); UUID mapId = UUID.randomUUID(); UUID tokenId = UUID.randomUUID();
        UUID pendingTurnId = UUID.randomUUID(); UUID commandId = UUID.randomUUID(); UUID operationId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class); CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class); AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        var pendingRepository = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class);
        RuntimeTurnRepository runtimeTurns = mock(RuntimeTurnRepository.class);
        var ruleSetService = mock(AppliedRuleSetApplicationService.class); Adventure adventure = mock(Adventure.class);
        var appliedRuleSet = mock(com.dndmaster.adventure.domain.ruleset.AppliedRuleSet.class);
        var path = List.of(new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(0, 0),
                new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 0));
        var pending = new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation(adventureId, ownerId, mapId, tokenId,
                4, path, 5, "natural-preview", List.of(), "문으로 가",
                path.getLast(), pendingTurnId);
        var check = new com.dndmaster.adventure.application.combat.CombatMapPendingCheck(UUID.randomUUID(), operationId,
                "지각 판정", "d20", ownerId, com.dndmaster.adventure.application.combat.CombatMapCheckActor.PLAYER);
        var result = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.CHECK_REQUIRED,
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)),
                List.of(new CombatMapPreviewPosition(0, 0)), new CombatMapPreviewPosition(0, 0), List.of(), "판정 대기", check);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.id()).thenReturn(new AdventureId(adventureId)); when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(adventure.sessionId()).thenReturn(new com.dndmaster.adventure.domain.adventure.SessionId(UUID.randomUUID()));
        UUID sessionId = adventure.sessionId().value();
        var turn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        when(turn.turnId()).thenReturn(pendingTurnId); when(turn.adventureId()).thenReturn(new AdventureId(adventureId));
        when(turn.sessionId()).thenReturn(sessionId); when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.RESOLVING);
        when(turn.origin()).thenReturn(RuntimeTurnOrigin.PLAYER); when(turn.playerOrigin()).thenReturn(true);
        when(turn.gmOnly()).thenReturn(false); when(turn.agentOrigin()).thenReturn(false);
        when(runtimeTurns.findByTurnId(pendingTurnId)).thenReturn(Optional.of(turn)); when(runtimeTurns.findAllByAdventureId(new AdventureId(adventureId))).thenReturn(List.of(turn));
        when(adventure.ruleSetId()).thenReturn(new com.dndmaster.adventure.domain.adventure.RuleSetId(UUID.randomUUID()));
        when(adventure.party()).thenReturn(List.of(new com.dndmaster.adventure.domain.adventure.AdventurePartyMember(
                new com.dndmaster.adventure.domain.adventure.CharacterSheetId(tokenId), com.dndmaster.adventure.domain.adventure.ControlMode.DIRECT,
                true, true, true, true, true, true)));
        when(players.playerId()).thenReturn(ownerId); when(pendingRepository.findByAdventureId(adventureId, ownerId)).thenReturn(Optional.of(pending));
        when(ruleSetService.readRuleSet(any(), any())).thenReturn(appliedRuleSet);
        when(appliedRuleSet.edition()).thenReturn(new com.dndmaster.adventure.domain.ruleset.DndEdition("DND_5E_2024"));
        when(combatMap.preview(any())).thenReturn(new CombatMapPreviewResult(mapId,
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)), 5, 4, "natural-preview"));
        when(combatMap.move(any())).thenReturn(result);

        AdventureController controller = controller(adventures, combatMap, mapViews, players, pendingRepository, ruleSetService, runtimeTurns);

        var response = controller.confirmNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementConfirmationRequest(pendingTurnId, commandId, tokenId, 4L));

        assertEquals("CHECK_REQUIRED", response.status()); assertEquals(path.size(), response.requestedPath().size());
        assertEquals(4L, response.resultingVersion()); assertEquals(check, response.pendingCheck());
        verify(pendingRepository).save(org.mockito.ArgumentMatchers.argThat(saved -> !saved.terminal()
                && commandId.equals(saved.confirmationCommandId()) && saved.path().equals(path)));
    }

    @Test
    void terminal_natural_confirmation_replays_the_saved_result_without_restarting_movement() throws Exception {
        UUID adventureId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID(); UUID mapId = UUID.randomUUID(); UUID tokenId = UUID.randomUUID();
        UUID pendingTurnId = UUID.randomUUID(); UUID commandId = UUID.randomUUID(); UUID operationId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class); CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class); AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        RuntimeTurnRepository runtimeTurns = mock(RuntimeTurnRepository.class);
        var pendingRepository = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class);
        var ruleSetService = mock(AppliedRuleSetApplicationService.class); Adventure adventure = mock(Adventure.class);
        var appliedRuleSet = mock(com.dndmaster.adventure.domain.ruleset.AppliedRuleSet.class);
        var path = List.of(new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(0, 0),
                new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 0));
        var savedResult = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(5, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.COMMITTED,
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)),
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)),
                new CombatMapPreviewPosition(1, 0), List.of("이동 완료"), null);
        var pending = new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation(adventureId, ownerId, mapId, tokenId,
                4, path, 5, "natural-preview", List.of(), "문으로 가", path.getLast(), pendingTurnId, commandId, true,
                new ObjectMapper().writeValueAsString(savedResult));
        var turn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        UUID sessionId = UUID.randomUUID();
        when(turn.turnId()).thenReturn(pendingTurnId); when(turn.adventureId()).thenReturn(new AdventureId(adventureId));
        when(turn.sessionId()).thenReturn(sessionId); when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.DISCARDED);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId)); when(adventure.id()).thenReturn(new AdventureId(adventureId));
        when(adventure.sessionId()).thenReturn(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId));
        when(adventure.ruleSetId()).thenReturn(new com.dndmaster.adventure.domain.adventure.RuleSetId(UUID.randomUUID()));
        when(players.playerId()).thenReturn(ownerId); when(pendingRepository.findByAdventureId(adventureId, ownerId)).thenReturn(Optional.of(pending));
        when(runtimeTurns.findByTurnId(pendingTurnId)).thenReturn(Optional.of(turn)); when(runtimeTurns.findAllByAdventureId(new AdventureId(adventureId))).thenReturn(List.of(turn));
        when(ruleSetService.readRuleSet(any(), any())).thenReturn(appliedRuleSet); when(appliedRuleSet.edition()).thenReturn(new com.dndmaster.adventure.domain.ruleset.DndEdition("DND_5E_2024"));
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(mapId, new CombatMapViewPort.Grid(3, 3, 50, 5), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 4)));

        AdventureController controller = controller(adventures, combatMap, mapViews, players, pendingRepository, ruleSetService, runtimeTurns);
        var first = controller.confirmNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementConfirmationRequest(pendingTurnId, commandId, tokenId, 4L));
        var second = controller.confirmNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementConfirmationRequest(pendingTurnId, commandId, tokenId, 4L));

        assertEquals(first.operationId(), second.operationId());
        verify(combatMap, times(0)).move(any());
    }

    @Test
    void terminal_natural_confirmation_replays_after_a_later_runtime_turn_exists() throws Exception {
        UUID adventureId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID(); UUID mapId = UUID.randomUUID(); UUID tokenId = UUID.randomUUID();
        UUID pendingTurnId = UUID.randomUUID(); UUID laterTurnId = UUID.randomUUID(); UUID commandId = UUID.randomUUID(); UUID operationId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class); CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class); AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        RuntimeTurnRepository runtimeTurns = mock(RuntimeTurnRepository.class);
        var pendingRepository = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class);
        var ruleSetService = mock(AppliedRuleSetApplicationService.class); Adventure adventure = mock(Adventure.class);
        var path = List.of(new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(0, 0),
                new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 0));
        var savedResult = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(5, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.COMMITTED,
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)),
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)),
                new CombatMapPreviewPosition(1, 0), List.of("이동 완료"), null);
        var pending = new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation(adventureId, ownerId, mapId, tokenId,
                4, path, 5, "natural-preview", List.of(), "문으로 가", path.getLast(), pendingTurnId, commandId, true,
                new ObjectMapper().writeValueAsString(savedResult));
        var originalTurn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        var laterTurn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        UUID sessionId = UUID.randomUUID();
        when(originalTurn.turnId()).thenReturn(pendingTurnId); when(originalTurn.adventureId()).thenReturn(new AdventureId(adventureId));
        when(originalTurn.sessionId()).thenReturn(sessionId); when(originalTurn.lifecycle()).thenReturn(RuntimeTurnLifecycle.PRESENTED);
        when(laterTurn.turnId()).thenReturn(laterTurnId); when(laterTurn.adventureId()).thenReturn(new AdventureId(adventureId));
        when(laterTurn.sessionId()).thenReturn(sessionId); when(laterTurn.lifecycle()).thenReturn(RuntimeTurnLifecycle.RESOLVING);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId)); when(adventure.id()).thenReturn(new AdventureId(adventureId));
        when(adventure.sessionId()).thenReturn(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId));
        when(adventure.ruleSetId()).thenReturn(new com.dndmaster.adventure.domain.adventure.RuleSetId(UUID.randomUUID()));
        when(players.playerId()).thenReturn(ownerId); when(pendingRepository.findByAdventureId(adventureId, ownerId)).thenReturn(Optional.of(pending));
        when(runtimeTurns.findByTurnId(pendingTurnId)).thenReturn(Optional.of(originalTurn));
        when(runtimeTurns.findAllByAdventureId(new AdventureId(adventureId))).thenReturn(List.of(originalTurn, laterTurn));

        AdventureController controller = controller(adventures, combatMap, mapViews, players, pendingRepository, ruleSetService, runtimeTurns);
        var response = controller.confirmNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementConfirmationRequest(pendingTurnId, commandId, tokenId, 4L));

        assertEquals(savedResult.operationId(), response.operationId());
        verify(combatMap, times(0)).move(any());
    }

    @Test
    void rejects_nonterminal_confirmation_when_the_runtime_turn_is_completed() {
        UUID adventureId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID(); UUID mapId = UUID.randomUUID(); UUID tokenId = UUID.randomUUID();
        UUID pendingTurnId = UUID.randomUUID(); UUID commandId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class); CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class); AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        RuntimeTurnRepository runtimeTurns = mock(RuntimeTurnRepository.class);
        var pendingRepository = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class);
        Adventure adventure = mock(Adventure.class);
        var path = List.of(new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(0, 0),
                new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 0));
        var pending = new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation(adventureId, ownerId, mapId, tokenId,
                4, path, 5, "natural-preview", List.of(), "문으로 가", path.getLast(), pendingTurnId, commandId, false);
        var turn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        UUID sessionId = UUID.randomUUID();
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.id()).thenReturn(new AdventureId(adventureId)); when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(adventure.sessionId()).thenReturn(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId));
        when(players.playerId()).thenReturn(ownerId); when(pendingRepository.findByAdventureId(adventureId, ownerId)).thenReturn(Optional.of(pending));
        when(runtimeTurns.findByTurnId(pendingTurnId)).thenReturn(Optional.of(turn));
        when(turn.turnId()).thenReturn(pendingTurnId); when(turn.adventureId()).thenReturn(new AdventureId(adventureId));
        when(turn.sessionId()).thenReturn(sessionId); when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.PRESENTED);

        AdventureController controller = controller(adventures, combatMap, mapViews, players, pendingRepository,
                mock(AppliedRuleSetApplicationService.class), runtimeTurns);
        var thrown = assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.confirmNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementConfirmationRequest(pendingTurnId, commandId, tokenId, 4L)));

        assertEquals("STALE_MOVEMENT_PROPOSAL", thrown.getMessage());
        verifyNoInteractions(combatMap);
    }

    @Test
    void rejects_nonterminal_confirmation_for_a_gm_runtime_turn() {
        UUID adventureId = UUID.randomUUID(); UUID ownerId = UUID.randomUUID(); UUID mapId = UUID.randomUUID(); UUID tokenId = UUID.randomUUID();
        UUID pendingTurnId = UUID.randomUUID(); UUID commandId = UUID.randomUUID(); UUID sessionId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class); CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class); AuthenticatedPlayerResolver players = mock(AuthenticatedPlayerResolver.class);
        RuntimeTurnRepository runtimeTurns = mock(RuntimeTurnRepository.class);
        var pendingRepository = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class);
        Adventure adventure = mock(Adventure.class);
        var path = List.of(new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(0, 0),
                new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation.Position(1, 0));
        var pending = new com.dndmaster.adventure.domain.runtime.PendingMapMovementConfirmation(adventureId, ownerId, mapId, tokenId,
                4, path, 5, "natural-preview", List.of(), "문으로 가", path.getLast(), pendingTurnId, commandId, false);
        var turn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.id()).thenReturn(new AdventureId(adventureId)); when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(adventure.sessionId()).thenReturn(new com.dndmaster.adventure.domain.adventure.SessionId(sessionId));
        when(players.playerId()).thenReturn(ownerId); when(pendingRepository.findByAdventureId(adventureId, ownerId)).thenReturn(Optional.of(pending));
        when(runtimeTurns.findByTurnId(pendingTurnId)).thenReturn(Optional.of(turn));
        when(turn.turnId()).thenReturn(pendingTurnId); when(turn.adventureId()).thenReturn(new AdventureId(adventureId));
        when(turn.sessionId()).thenReturn(sessionId); when(turn.lifecycle()).thenReturn(RuntimeTurnLifecycle.RESOLVING);
        when(turn.origin()).thenReturn(RuntimeTurnOrigin.GM); when(turn.playerOrigin()).thenReturn(false);
        when(turn.gmOnly()).thenReturn(true); when(turn.agentOrigin()).thenReturn(false);

        AdventureController controller = controller(adventures, combatMap, mapViews, players, pendingRepository,
                mock(AppliedRuleSetApplicationService.class), runtimeTurns);
        var thrown = assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.confirmNaturalLanguageMovement(adventureId,
                new AdventureController.NaturalLanguageMovementConfirmationRequest(pendingTurnId, commandId, tokenId, 4L)));

        assertEquals("STALE_MOVEMENT_PROPOSAL", thrown.getMessage());
        verifyNoInteractions(combatMap);
    }

    @Test
    void rejects_malformed_preview_before_calling_combat_map() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class);
        CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        Adventure adventure = mock(Adventure.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(2, 2, 50, 5), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));

        AdventureController controller = controller(adventures, combatMap, mapViews, playerResolver);
        List<AdventureController.PositionPayload> waypoints = java.util.stream.IntStream.range(0, 17)
                .mapToObj(index -> new AdventureController.PositionPayload(1, 1)).toList();

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(mapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(1, 1), waypoints)));
        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(mapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(null, 1), List.of())));
        verifyNoInteractions(combatMap);
    }

    @Test
    void rejects_a_preview_with_a_null_waypoint_before_calling_combat_map() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class);
        CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        Adventure adventure = mock(Adventure.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(2, 2, 50, 5), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));

        AdventureController controller = controller(adventures, combatMap, mapViews, playerResolver);

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> controller.previewMovement(adventureId,
                new AdventureController.CombatMapMovementPreviewRequest(mapId, 0L, UUID.randomUUID(),
                        new AdventureController.PositionPayload(1, 1), java.util.Arrays.asList((AdventureController.PositionPayload) null))));
        verifyNoInteractions(combatMap);
    }

    @Test
    void saves_a_preview_as_an_adventure_owned_confirmation_for_reconnect() {
        UUID adventureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        UUID tokenId = UUID.randomUUID();
        AdventureRepository adventures = mock(AdventureRepository.class);
        CombatMapPort combatMap = mock(CombatMapPort.class);
        CombatMapViewPort mapViews = mock(CombatMapViewPort.class);
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        var pendingRepository = mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class);
        var ruleSetService = mock(AppliedRuleSetApplicationService.class);
        Adventure adventure = mock(Adventure.class);
        var appliedRuleSet = mock(com.dndmaster.adventure.domain.ruleset.AppliedRuleSet.class);
        when(adventures.findById(new AdventureId(adventureId))).thenReturn(Optional.of(adventure));
        when(adventure.id()).thenReturn(new AdventureId(adventureId));
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(ownerId));
        when(adventure.ruleSetId()).thenReturn(new com.dndmaster.adventure.domain.adventure.RuleSetId(UUID.randomUUID()));
        when(playerResolver.playerId()).thenReturn(ownerId);
        when(mapViews.playerView(adventureId, ownerId)).thenReturn(Optional.of(new CombatMapViewPort.View(
                mapId, new CombatMapViewPort.Grid(2, 2, 50, 5), List.of(), List.of(), List.of(),
                List.of(), List.of(), 0)));
        when(ruleSetService.readRuleSet(any(), any())).thenReturn(appliedRuleSet);
        when(appliedRuleSet.edition()).thenReturn(new com.dndmaster.adventure.domain.ruleset.DndEdition("DND_5E_2024"));
        when(combatMap.preview(any())).thenReturn(new CombatMapPreviewResult(mapId,
                List.of(new CombatMapPreviewPosition(0, 0), new CombatMapPreviewPosition(1, 0)), 5, 3, "preview"));

        AdventureController controller = controller(adventures, combatMap, mapViews, playerResolver, pendingRepository, ruleSetService);

        controller.previewMovement(adventureId, new AdventureController.CombatMapMovementPreviewRequest(
                mapId, 3L, tokenId, new AdventureController.PositionPayload(1, 0), List.of()));

        verify(pendingRepository).save(org.mockito.ArgumentMatchers.argThat(pending ->
                pending.adventureId().equals(adventureId) && pending.ownerPlayerId().equals(ownerId)
                        && pending.mapId().equals(mapId) && pending.tokenId().equals(tokenId)
                        && pending.path().size() == 2 && pending.fingerprint().equals("preview")));
    }

    @Test
    void rejects_confirmed_move_without_server_preview_fingerprint() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("validateConfirmedMapPreview", Adventure.class,
                UUID.class, AdventureController.MapActionPayload.class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), new AdventureController.MapActionPayload(
                        UUID.randomUUID(), 0L, UUID.randomUUID(), "MOVE", List.of(
                                new AdventureController.PositionPayload(0, 0), new AdventureController.PositionPayload(1, 0)),
                        null, new AdventureController.PositionPayload(1, 0), List.of(), null)));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_confirmed_move_without_an_explicit_map_version() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("validateConfirmedMapPreview", Adventure.class,
                UUID.class, AdventureController.MapActionPayload.class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), new AdventureController.MapActionPayload(
                        UUID.randomUUID(), null, UUID.randomUUID(), "MOVE", List.of(
                                new AdventureController.PositionPayload(0, 0), new AdventureController.PositionPayload(1, 0)),
                        null, new AdventureController.PositionPayload(1, 0), List.of(), "preview")));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_confirmed_move_without_a_waypoint_binding() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("validateConfirmedMapPreview", Adventure.class,
                UUID.class, AdventureController.MapActionPayload.class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), new AdventureController.MapActionPayload(
                        UUID.randomUUID(), 0L, UUID.randomUUID(), "MOVE", List.of(
                                new AdventureController.PositionPayload(0, 0), new AdventureController.PositionPayload(1, 0)),
                        null, new AdventureController.PositionPayload(1, 0), null, "preview")));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_confirmed_move_with_a_malformed_path_before_preview() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("validateConfirmedMapPreview", Adventure.class,
                UUID.class, AdventureController.MapActionPayload.class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), new AdventureController.MapActionPayload(
                        UUID.randomUUID(), 0L, UUID.randomUUID(), "MOVE", java.util.Arrays.asList(
                                new AdventureController.PositionPayload(0, 0), null),
                        null, null, List.of(), "preview")));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_confirmed_move_without_a_token_before_resolving_party_ownership() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("applyMapAction", Adventure.class, UUID.class,
                UUID.class, com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput.class);
        method.setAccessible(true);

        UUID mapId = UUID.randomUUID();
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(
                mapId, 0, "{\"mapId\":\"" + mapId
                        + "\",\"mapVersion\":0,\"action\":\"MOVE\",\"path\":[{\"x\":0,\"y\":0},{\"x\":1,\"y\":0}],\"fingerprint\":\"preview\"}");

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), UUID.randomUUID(), input));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_a_null_durable_confirmation_document_as_a_typed_error() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("prepareMapCommand", Adventure.class, UUID.class,
                UUID.class, UUID.class, com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput.class);
        method.setAccessible(true);

        UUID mapId = UUID.randomUUID();
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 0, "null");
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), input));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    @Test
    void rejects_a_map_action_without_outer_map_binding_as_a_typed_error() {
        assertThrows(ApiRequestGuard.ApiContractException.class, () ->
                new AdventureController.GmInputRequest("MAP_ACTION", null, null, null, "MOVE", null).toDomain());
    }

    @Test
    void rejects_a_confirmation_with_a_blank_inner_action_as_a_typed_error() throws Exception {
        AdventureController controller = controller(mock(AdventureRepository.class), mock(CombatMapPort.class),
                mock(CombatMapViewPort.class), mock(AuthenticatedPlayerResolver.class));
        var method = AdventureController.class.getDeclaredMethod("prepareMapCommand", Adventure.class, UUID.class,
                UUID.class, UUID.class, com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput.class);
        method.setAccessible(true);

        UUID mapId = UUID.randomUUID();
        var input = new com.dndmaster.adventure.domain.runtime.GmInput.MapActionInput(mapId, 0,
                "{\"mapId\":\"" + mapId + "\",\"mapVersion\":0,\"action\":\" \"}");
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(controller,
                mock(Adventure.class), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), input));

        assertThrows(ApiRequestGuard.ApiContractException.class, () -> {
            throw (ApiRequestGuard.ApiContractException) thrown.getCause();
        });
    }

    private static AdventureController controller(AdventureRepository adventures, CombatMapPort combatMap,
            CombatMapViewPort mapViews, AuthenticatedPlayerResolver playerResolver) {
        return controller(adventures, combatMap, mapViews, playerResolver,
                mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class),
                mock(AppliedRuleSetApplicationService.class));
    }

    private static AdventureController controller(AdventureRepository adventures, CombatMapPort combatMap,
            CombatMapViewPort mapViews, AuthenticatedPlayerResolver playerResolver,
            com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository pendingRepository,
            AppliedRuleSetApplicationService ruleSetService) {
        return new AdventureController(
                mock(SavedAdventureApplicationService.class), mock(RuntimeTurnApplicationService.class), adventures,
                mock(GmTurnFailureRecorder.class), mock(GmTurnRepository.class), mock(RuntimeTurnRepository.class),
                mock(SessionEventRepository.class), mock(RuleGuidanceApplicationService.class),
                mock(AdventureCombatApplicationService.class), mock(CombatActionApplicationService.class),
                mock(AdventureScenarioApplicationService.class), playerResolver, provider(combatMap),
                provider(mock(com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort.class)),
                provider(mock(CharacterCombatPort.class)), new ObjectMapper(), provider(mapViews),
                provider(mock(CombatMapPreparationPort.class)), pendingRepository, mock(ScenarioPackageRepository.class),
                mock(CombatLifecycleApplicationService.class), ruleSetService);
    }

    private static AdventureController controller(AdventureRepository adventures, CombatMapPort combatMap,
            CombatMapViewPort mapViews, AuthenticatedPlayerResolver playerResolver,
            com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository pendingRepository,
            AppliedRuleSetApplicationService ruleSetService, RuntimeTurnRepository runtimeTurns) {
        return new AdventureController(
                mock(SavedAdventureApplicationService.class), mock(RuntimeTurnApplicationService.class), adventures,
                mock(GmTurnFailureRecorder.class), mock(GmTurnRepository.class), runtimeTurns,
                mock(SessionEventRepository.class), mock(RuleGuidanceApplicationService.class),
                mock(AdventureCombatApplicationService.class), mock(CombatActionApplicationService.class),
                mock(AdventureScenarioApplicationService.class), playerResolver, provider(combatMap),
                provider(mock(com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort.class)),
                provider(mock(CharacterCombatPort.class)), new ObjectMapper(), provider(mapViews),
                provider(mock(CombatMapPreparationPort.class)), pendingRepository, mock(ScenarioPackageRepository.class),
                mock(CombatLifecycleApplicationService.class), ruleSetService);
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return value; }
            @Override public T getIfAvailable(java.util.function.Supplier<T> fallback) { return value; }
        };
    }
}
