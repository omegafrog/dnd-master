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
import com.dndmaster.adventure.application.combat.CombatWorkItem;
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
    void combat_snapshot_does_not_create_or_schedule_an_ai_request() {
        Fixture fixture = fixture();
        UUID aiActor = UUID.randomUUID();
        when(fixture.encounters().findActive(fixture.adventure().id().value())).thenReturn(Optional.of(
                com.dndmaster.adventure.domain.combat.CombatStartPolicy.startFromCommittedGmTurn(true,
                        fixture.adventure().id().value(), java.util.List.of(new com.dndmaster.adventure.domain.combat.CombatParticipant(
                                aiActor, "적", com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI, 10, null)))));
        when(fixture.workItems().hasPendingForEncounter(any())).thenReturn(false);

        fixture.combatController().snapshot(fixture.adventure().id().value());

        verify(fixture.scheduler(), never()).scheduleNext(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
        verify(fixture.aiRequests(), never()).begin(any(), any(), any());
    }

    @Test
    void manual_combat_retry_reacquires_the_session_request_with_a_new_request_id() {
        AdventureSessionRepository sessions = mock(AdventureSessionRepository.class);
        UUID requestId = UUID.randomUUID();
        when(sessions.tryAcquireAiRequest(any(), any(), org.mockito.ArgumentMatchers.eq(requestId))).thenReturn(true);
        Fixture fixture = fixture(new AdventureAiRequestApplicationService(sessions));
        UUID operationId = UUID.randomUUID();
        UUID priorRequestId = UUID.randomUUID();
        var command = new com.dndmaster.adventure.application.combat.CombatActionCommand(operationId,
                fixture.adventure().id(), fixture.adventure().sessionId().value(), fixture.adventure().ruleSetId(),
                new CharacterSheetId(UUID.randomUUID()), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "AI_TURN", null,
                fixture.adventure().ownerPlayerId().value(), UUID.randomUUID(), 1);
        var failed = CombatWorkItem.restore(UUID.randomUUID(), UUID.randomUUID(), operationId, 1,
                CombatWorkItem.WorkType.AI_TURN, java.time.Instant.now(), 1, CombatWorkItem.Status.FAILED,
                null, null, "AI unavailable", com.dndmaster.adventure.application.combat.AiTacticalInstructionContext.none(),
                command, 0, priorRequestId);
        when(fixture.workItems().findByOperationId(operationId)).thenReturn(Optional.of(failed));

        fixture.combatController().retry(fixture.adventure().id().value(), requestId.toString(),
                new CombatController.RetryRequest(operationId));

        verify(sessions).tryAcquireAiRequest(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);
        verify(sessions, never()).releaseAiRequest(any(), any(), any());
        verify(fixture.workItems()).save(org.mockito.ArgumentMatchers.argThat(item ->
                item.aiRequestId().equals(requestId) && item.status() == CombatWorkItem.Status.PENDING));
    }

    @Test
    void manual_combat_retry_rejects_a_failed_work_item_from_another_adventure() {
        Fixture fixture = fixture();
        UUID operationId = UUID.randomUUID();
        var foreignCommand = new com.dndmaster.adventure.application.combat.CombatActionCommand(operationId,
                AdventureId.generate(), UUID.randomUUID(), new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()), null,
                com.dndmaster.adventure.application.combat.CombatActorRole.AI, "AI_TURN", null,
                UUID.randomUUID(), UUID.randomUUID(), 1);
        var failed = CombatWorkItem.restore(UUID.randomUUID(), UUID.randomUUID(), operationId, 1,
                CombatWorkItem.WorkType.AI_TURN, java.time.Instant.now(), 1, CombatWorkItem.Status.FAILED,
                null, null, "AI unavailable", com.dndmaster.adventure.application.combat.AiTacticalInstructionContext.none(),
                foreignCommand, 0, UUID.randomUUID());
        when(fixture.workItems().findByOperationId(operationId)).thenReturn(Optional.of(failed));

        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> fixture.combatController().retry(
                fixture.adventure().id().value(), UUID.randomUUID().toString(), new CombatController.RetryRequest(operationId)));

        verify(fixture.aiRequests(), never()).begin(any(), any(), any());
        verify(fixture.workItems(), never()).save(any());
    }

    @Test
    void result_processing_error_preserves_the_committed_state_records_the_error_returns_it_and_releases_its_request() {
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
        org.mockito.Mockito.doNothing().doNothing().doThrow(new IllegalStateException("committed turn store unavailable"))
                .when(fixture.gmTurns()).save(any(), org.mockito.ArgumentMatchers.eq(fixture.adventure().id().value()));

        var response = fixture.adventureController().submitTypedTurn(
                fixture.adventure().id().value(), requestId, fixture.adventure().version(),
                new AdventureController.GmTurnRequest(UUID.randomUUID(),
                        new AdventureController.GmInputRequest("TEXT", "문을 살펴본다", null, null, null, null)));

        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("GM_TURN_RESULT_PROCESSING_FAILED", ((java.util.Map<?, ?>) response.getBody()).get("error"));
        verify(sessions).releaseAiRequest(fixture.adventure().sessionId(), fixture.adventure().ownerPlayerId(), requestId);
        UUID resultTurnId = resultTurn.turnId();
        verify(fixture.gmTurnFailures()).recordResultProcessingFailure(
                org.mockito.ArgumentMatchers.eq(fixture.adventure().sessionId().value()),
                org.mockito.ArgumentMatchers.eq(resultTurnId), org.mockito.ArgumentMatchers.eq(requestId),
                org.mockito.ArgumentMatchers.eq(fixture.adventure().version()), org.mockito.ArgumentMatchers.argThat(failure ->
                        failure instanceof IllegalStateException
                                && "committed turn store unavailable".equals(failure.getMessage())));
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
