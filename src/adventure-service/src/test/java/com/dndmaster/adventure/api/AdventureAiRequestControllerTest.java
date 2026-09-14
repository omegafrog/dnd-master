package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.combat.AdventureCombatApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatWorkItemRepository;
import com.dndmaster.adventure.application.combat.CombatWorkItemScheduler;
import com.dndmaster.adventure.application.runtime.GmTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.session.AdventureAiRequestApplicationService;
import com.dndmaster.adventure.application.session.AdventureAiRequestInProgressException;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AdventureAiRequestControllerTest {
    @Test
    void competing_chat_or_map_action_is_rejected_before_any_turn_is_saved() {
        Fixture fixture = fixture();
        UUID requestId = UUID.randomUUID();
        doThrow(new AdventureAiRequestInProgressException()).when(fixture.aiRequests())
                .begin(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);

        assertThrows(AdventureAiRequestInProgressException.class, () -> fixture.adventureController().submitTypedTurn(
                fixture.adventure().id().value(), requestId, fixture.adventure().version(),
                new AdventureController.GmTurnRequest(UUID.randomUUID(),
                        new AdventureController.GmInputRequest("TEXT", "문을 살펴본다", null, null, null, null))));

        verify(fixture.gmTurns(), never()).save(any(), any());
        verify(fixture.runtimeTurns(), never()).submitTurn(any());
    }

    @Test
    void competing_combat_action_is_rejected_before_the_action_service_can_persist_it() {
        Fixture fixture = fixture();
        UUID requestId = UUID.randomUUID();
        doThrow(new AdventureAiRequestInProgressException()).when(fixture.aiRequests())
                .begin(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);

        assertThrows(AdventureAiRequestInProgressException.class, () -> fixture.combatController().action(
                fixture.adventure().id().value(), requestId.toString(), fixture.adventure().version(),
                new CombatController.CombatActionRequest(UUID.randomUUID(), "공격", null, null, null, null)));

        verify(fixture.combatActions(), never()).submit(any());
    }

    @Test
    void competing_combat_turn_end_is_rejected_before_it_can_schedule_an_ai_follow_up() {
        Fixture fixture = fixture();
        UUID requestId = UUID.randomUUID();
        doThrow(new AdventureAiRequestInProgressException()).when(fixture.aiRequests())
                .begin(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);

        assertThrows(AdventureAiRequestInProgressException.class, () -> fixture.combatController().endTurn(
                fixture.adventure().id().value(), requestId.toString(), fixture.adventure().version(),
                new CombatController.TurnEndRequest(UUID.randomUUID())));

        verify(fixture.combatActions(), never()).endTurn(any());
        verify(fixture.scheduler(), never()).scheduleNext(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void combat_snapshot_does_not_schedule_an_ai_turn_while_another_request_is_active() {
        Fixture fixture = fixture();
        UUID requestId = UUID.randomUUID();
        UUID aiActor = UUID.randomUUID();
        when(fixture.encounters().findActive(fixture.adventure().id().value())).thenReturn(Optional.of(
                CombatStartPolicy.startFromCommittedGmTurn(true, fixture.adventure().id().value(), java.util.List.of(
                        new CombatParticipant(aiActor, "적", CombatParticipant.Controller.AI, 10, null)))));
        when(fixture.workItems().hasPendingForEncounter(any())).thenReturn(false);
        doThrow(new AdventureAiRequestInProgressException()).when(fixture.aiRequests()).begin(
                org.mockito.ArgumentMatchers.eq(fixture.adventure().sessionId()),
                org.mockito.ArgumentMatchers.eq(fixture.adventure().ownerPlayerId()), org.mockito.ArgumentMatchers.any());

        assertThrows(AdventureAiRequestInProgressException.class,
                () -> fixture.combatController().snapshot(fixture.adventure().id().value()));

        verify(fixture.scheduler(), never()).scheduleNext(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void result_processing_error_preserves_the_committed_state_returns_an_error_and_releases_its_request() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        UUID requestId = UUID.randomUUID();
        when(sessions.tryAcquireAiRequest(any(), any(), org.mockito.ArgumentMatchers.eq(requestId))).thenReturn(true);
        when(sessions.releaseAiRequest(any(), any(), org.mockito.ArgumentMatchers.eq(requestId))).thenReturn(true);
        Fixture fixture = fixture(new AdventureAiRequestApplicationService(sessions));
        var plan = new com.dndmaster.adventure.application.runtime.RuntimePlan(
                "장면", null, "판정", "서술", null, java.util.List.of(), java.util.List.of());
        var resultTurn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        when(resultTurn.plan()).thenReturn(plan);
        when(resultTurn.sessionId()).thenReturn(fixture.adventure().sessionId().value());
        when(resultTurn.turnId()).thenReturn(UUID.randomUUID());
        when(fixture.runtimeTurns().submitTurn(any())).thenReturn(
                new com.dndmaster.adventure.application.runtime.RuntimeTurnResult(resultTurn,
                        fixture.adventure().currentContext(), java.util.List.of(), fixture.adventure().version()));
        doThrow(new IllegalStateException("event store unavailable")).when(fixture.sessionEvents()).append(any());

        var response = fixture.adventureController().submitTypedTurn(
                fixture.adventure().id().value(), requestId, fixture.adventure().version(),
                new AdventureController.GmTurnRequest(UUID.randomUUID(),
                        new AdventureController.GmInputRequest("TEXT", "문을 살펴본다", null, null, null, null)));

        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("GM_TURN_RESULT_PROCESSING_FAILED", ((java.util.Map<?, ?>) response.getBody()).get("error"));
        verify(sessions).releaseAiRequest(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);
        verifyNoInteractions(fixture.gmTurnFailures());
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture() {
        return fixture(mock(AdventureAiRequestApplicationService.class));
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(AdventureAiRequestApplicationService aiRequests) {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        Adventure adventure = Adventure.create(AdventureId.generate(), SessionId.generate(), owner,
                new ScenarioId(UUID.randomUUID()), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), new AdventureContext("장면", null, null, null));
        AdventureRepository adventures = mock(AdventureRepository.class);
        when(adventures.findById(adventure.id())).thenReturn(Optional.of(adventure));
        AuthenticatedPlayerResolver playerResolver = mock(AuthenticatedPlayerResolver.class);
        when(playerResolver.playerId()).thenReturn(owner.value());
        GmTurnRepository gmTurns = mock(GmTurnRepository.class);
        when(gmTurns.findByCommandId(any())).thenReturn(Optional.empty());
        RuntimeTurnApplicationService runtimeTurns = mock(RuntimeTurnApplicationService.class);
        CombatActionApplicationService combatActions = mock(CombatActionApplicationService.class);
        com.dndmaster.adventure.application.combat.CombatEncounterRepository encounters = mock(com.dndmaster.adventure.application.combat.CombatEncounterRepository.class);
        CombatWorkItemRepository workItems = mock(CombatWorkItemRepository.class);
        CombatWorkItemScheduler scheduler = mock(CombatWorkItemScheduler.class);
        com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder gmTurnFailures =
                mock(com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder.class);
        com.dndmaster.adventure.application.runtime.SessionEventRepository sessionEvents =
                mock(com.dndmaster.adventure.application.runtime.SessionEventRepository.class);

        AdventureController adventureController = new AdventureController(
                mock(com.dndmaster.adventure.application.saved.SavedAdventureApplicationService.class), runtimeTurns,
                adventures, gmTurnFailures, gmTurns,
                mock(RuntimeTurnRepository.class), sessionEvents,
                mock(com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService.class),
                mock(AdventureCombatApplicationService.class), combatActions,
                mock(com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService.class),
                playerResolver, mock(ObjectProvider.class), mock(ObjectProvider.class), new ObjectMapper(),
                mock(ObjectProvider.class), mock(ObjectProvider.class),
                mock(com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository.class),
                mock(com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService.class), aiRequests);
        CombatController combatController = new CombatController(
                encounters, playerResolver,
                adventures, mock(com.dndmaster.adventure.application.combat.CombatEventRepository.class), combatActions,
                mock(com.dndmaster.adventure.application.combat.CombatReactionApplicationService.class),
                workItems, scheduler,
                mock(com.dndmaster.adventure.application.combat.CharacterCombatPort.class), aiRequests);
        return new Fixture(adventure, adventureController, combatController, aiRequests, gmTurns, runtimeTurns,
                combatActions, encounters, workItems, scheduler, gmTurnFailures, sessionEvents);
    }

    private record Fixture(Adventure adventure, AdventureController adventureController,
            CombatController combatController, AdventureAiRequestApplicationService aiRequests,
            GmTurnRepository gmTurns, RuntimeTurnApplicationService runtimeTurns,
            CombatActionApplicationService combatActions,
            com.dndmaster.adventure.application.combat.CombatEncounterRepository encounters,
            CombatWorkItemRepository workItems, CombatWorkItemScheduler scheduler,
            com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder gmTurnFailures,
            com.dndmaster.adventure.application.runtime.SessionEventRepository sessionEvents) { }
}
