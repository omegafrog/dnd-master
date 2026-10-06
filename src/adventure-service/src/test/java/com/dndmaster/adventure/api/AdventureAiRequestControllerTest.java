package com.dndmaster.adventure.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AdventureAiRequestControllerTest {
    @Test
    void completed_player_roll_commits_the_matching_gm_turn_and_publishes_completion_event() {
        Fixture fixture = fixture();
        UUID commandId = UUID.randomUUID();
        UUID gmTurnId = UUID.randomUUID();
        UUID runtimeTurnId = UUID.randomUUID();
        var gmTurn = com.dndmaster.adventure.domain.runtime.GmTurn.start(gmTurnId, commandId,
                fixture.adventure().version(), new com.dndmaster.adventure.domain.runtime.GmInput.TextInput("저장고를 살핀다"))
                .process();
        when(fixture.gmTurns().findByCommandId(commandId)).thenReturn(Optional.of(gmTurn));

        var runtimeTurn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        var plan = new com.dndmaster.adventure.application.runtime.RuntimePlan(
                "저장고", null, "지각 판정 성공", "쥐를 발견한다", null, java.util.List.of(), java.util.List.of());
        when(runtimeTurn.turnId()).thenReturn(runtimeTurnId);
        when(runtimeTurn.commandId()).thenReturn(commandId);
        when(runtimeTurn.adventureId()).thenReturn(fixture.adventure().id());
        when(runtimeTurn.sessionId()).thenReturn(fixture.adventure().sessionId().value());
        when(runtimeTurn.plan()).thenReturn(plan);
        when(runtimeTurn.lifecycle()).thenReturn(com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.PRESENTED);
        when(fixture.runtimeTurns().submitPlayerRoll(any())).thenReturn(
                new com.dndmaster.adventure.application.runtime.RuntimeTurnResult(runtimeTurn,
                        fixture.adventure().currentContext(), java.util.List.of(), fixture.adventure().version() + 1));

        fixture.adventureController().submitPlayerRoll(fixture.adventure().id().value(), runtimeTurnId,
                new AdventureController.PlayerRollRequest(9, fixture.adventure().version()));

        verify(fixture.gmTurns()).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.commandId().equals(commandId)
                        && saved.status() == com.dndmaster.adventure.domain.runtime.GmTurnStatus.COMMITTED),
                org.mockito.ArgumentMatchers.eq(fixture.adventure().id().value()));
        verify(fixture.sessionEvents()).appendNext(org.mockito.ArgumentMatchers.eq(fixture.adventure().sessionId().value()), any(),
                org.mockito.ArgumentMatchers.eq("GM_TURN_COMMITTED"),
                org.mockito.ArgumentMatchers.eq(runtimeTurnId.toString()));
    }

    @Test
    void next_action_recovers_a_presented_turn_left_processing_by_an_older_roll_request() {
        Fixture fixture = fixture();
        UUID oldCommandId = UUID.randomUUID();
        UUID oldTurnId = UUID.randomUUID();
        var oldGmTurn = com.dndmaster.adventure.domain.runtime.GmTurn.start(oldTurnId, oldCommandId,
                0, new com.dndmaster.adventure.domain.runtime.GmInput.TextInput("저장고를 살핀다"))
                .process();
        when(fixture.gmTurns().findProcessingByAdventureId(fixture.adventure().id().value()))
                .thenReturn(Optional.of(oldGmTurn));
        when(fixture.gmTurns().findByCommandId(oldCommandId)).thenReturn(Optional.of(oldGmTurn));
        var presentedTurn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        when(presentedTurn.commandId()).thenReturn(oldCommandId);
        when(presentedTurn.turnId()).thenReturn(oldTurnId);
        when(presentedTurn.sessionId()).thenReturn(fixture.adventure().sessionId().value());
        when(presentedTurn.plan()).thenReturn(new com.dndmaster.adventure.application.runtime.RuntimePlan(
                "저장고", null, "지각 판정 성공", "쥐를 발견한다", null, java.util.List.of(), java.util.List.of()));
        when(presentedTurn.lifecycle()).thenReturn(com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.PRESENTED);
        when(fixture.runtimeTurnRepository().findByCommandId(oldCommandId)).thenReturn(Optional.of(presentedTurn));

        var response = fixture.adventureController().submitTypedTurn(fixture.adventure().id().value(),
                UUID.randomUUID(), fixture.adventure().version(),
                new AdventureController.GmTurnRequest(UUID.randomUUID(),
                        new AdventureController.GmInputRequest("TEXT", "쥐를 공격한다", null, null, null, null)));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("PREVIOUS_TURN_RECOVERED", ((java.util.Map<?, ?>) response.getBody()).get("error"));
        verify(fixture.gmTurns()).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.commandId().equals(oldCommandId)
                        && saved.status() == com.dndmaster.adventure.domain.runtime.GmTurnStatus.COMMITTED),
                org.mockito.ArgumentMatchers.eq(fixture.adventure().id().value()));
    }

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
    void combat_snapshot_projects_only_generic_block_for_terminal_enemy_evidence_failure() {
        Fixture fixture = fixture();
        UUID enemyId = UUID.randomUUID();
        var encounter = com.dndmaster.adventure.domain.combat.CombatStartPolicy.prepareFromCommittedGmTurn(true,
                fixture.adventure().id().value(), java.util.List.of(new com.dndmaster.adventure.domain.combat.CombatParticipant(
                        enemyId, "적", com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI,
                        10, "비밀 상태", com.dndmaster.adventure.domain.combat.TurnResources.initial(), null, null, "goblin")));
        when(fixture.encounters().findActive(fixture.adventure().id().value())).thenReturn(Optional.of(encounter));
        var identity = new com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity(
                fixture.adventure().id().value(), UUID.randomUUID(), 1, UUID.randomUUID(), java.util.List.of(UUID.randomUUID()), "goblin");
        var request = new com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest(UUID.randomUUID(),
                fixture.adventure().id().value(), java.util.List.of(new com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest.Enemy(
                        identity, new com.dndmaster.adventure.application.runtime.CombatEnemyProposal("scene", "goblin", "적", 1))));
        var pending = com.dndmaster.adventure.application.combat.CombatWorkItem.enemySheetPreparation(UUID.randomUUID(),
                encounter.encounterId(), encounter.version(), java.time.Instant.now(), request);
        UUID lease = UUID.randomUUID();
        var terminal = pending.claimed(lease, java.time.Instant.now().plusSeconds(30))
                .failed(lease, "internal provider detail and hidden stat block");
        when(fixture.workItems().findFailedByEncounterId(encounter.encounterId())).thenReturn(Optional.of(terminal));

        var response = fixture.combatController().snapshot(fixture.adventure().id().value());

        assertEquals(org.springframework.http.HttpStatus.OK, response.getStatusCode());
        var snapshot = (com.dndmaster.adventure.domain.combat.PlayerCombatSnapshot) response.getBody();
        assertNotNull(snapshot);
        assertEquals("COMBAT_PREPARATION_BLOCKED", snapshot.processingFailure().failure());
        assertFalse(snapshot.toString().contains("internal provider detail"));
        assertFalse(snapshot.toString().contains("hidden stat block"));
    }

    @Test
    void combat_snapshot_hides_internal_error_for_failed_ai_turn_work() {
        Fixture fixture = fixture();
        UUID enemyId = UUID.randomUUID();
        var encounter = com.dndmaster.adventure.domain.combat.CombatStartPolicy.startFromCommittedGmTurn(true,
                fixture.adventure().id().value(), java.util.List.of(new com.dndmaster.adventure.domain.combat.CombatParticipant(
                        enemyId, "적", com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.AI,
                        10, "비밀 상태", com.dndmaster.adventure.domain.combat.TurnResources.initial(), null, null, "goblin")));
        when(fixture.encounters().findActive(fixture.adventure().id().value())).thenReturn(Optional.of(encounter));
        var failed = com.dndmaster.adventure.application.combat.CombatWorkItem.restore(UUID.randomUUID(),
                encounter.encounterId(), UUID.randomUUID(), encounter.version(),
                com.dndmaster.adventure.application.combat.CombatWorkItem.WorkType.AI_TURN,
                java.time.Instant.now(), 1, com.dndmaster.adventure.application.combat.CombatWorkItem.Status.FAILED,
                null, null, "provider exception includes hidden stat block AC 19", null, null, 0);
        when(fixture.workItems().findFailedByEncounterId(encounter.encounterId())).thenReturn(Optional.of(failed));

        var response = fixture.combatController().snapshot(fixture.adventure().id().value());

        var snapshot = (com.dndmaster.adventure.domain.combat.PlayerCombatSnapshot) response.getBody();
        assertNotNull(snapshot);
        assertEquals("COMBAT_PROCESSING_FAILED", snapshot.processingFailure().failure());
        assertFalse(snapshot.toString().contains("provider exception"));
        assertFalse(snapshot.toString().contains("AC 19"));
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
        when(resultTurn.adventureId()).thenReturn(fixture.adventure().id());
        when(resultTurn.sessionId()).thenReturn(fixture.adventure().sessionId().value());
        when(resultTurn.turnId()).thenReturn(UUID.randomUUID());
        when(resultTurn.lifecycle()).thenReturn(
                com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMITTED);
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

    @Test
    void committed_turn_that_proposes_combat_does_not_fail_when_an_encounter_is_already_active() {
        Fixture fixture = fixture();
        UUID commandId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        var plan = new com.dndmaster.adventure.application.runtime.RuntimePlan(
                "저장고", null, "전투 중에는 짧은 휴식을 취할 수 없습니다.", "쥐가 계속 다가옵니다.", null,
                java.util.List.of(), java.util.List.of(), "test", "test-model", "", false, "", null, null,
                1, java.util.List.of(), null,
                java.util.List.of(new com.dndmaster.adventure.application.runtime.CombatEnemyProposal(
                        "encounter-rats", "거대 쥐", 1)), true, false);
        var resultTurn = mock(com.dndmaster.adventure.application.runtime.RuntimeTurn.class);
        when(resultTurn.plan()).thenReturn(plan);
        when(resultTurn.adventureId()).thenReturn(fixture.adventure().id());
        when(resultTurn.sessionId()).thenReturn(fixture.adventure().sessionId().value());
        when(resultTurn.turnId()).thenReturn(turnId);
        when(resultTurn.lifecycle()).thenReturn(com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle.COMMITTED);
        when(fixture.runtimeTurns().submitTurn(any())).thenReturn(
                new com.dndmaster.adventure.application.runtime.RuntimeTurnResult(resultTurn,
                        fixture.adventure().currentContext(), java.util.List.of(), fixture.adventure().version() + 1));
        var activeEncounter = com.dndmaster.adventure.domain.combat.CombatStartPolicy.startFromCommittedGmTurn(true,
                fixture.adventure().id().value(), java.util.List.of(new com.dndmaster.adventure.domain.combat.CombatParticipant(
                        UUID.randomUUID(), "영웅", com.dndmaster.adventure.domain.combat.CombatParticipant.Controller.PLAYER, 10, null)));
        when(fixture.combatLifecycle().findActiveEncounter(fixture.adventure().id().value()))
                .thenReturn(Optional.of(activeEncounter));

        var response = fixture.adventureController().submitTypedTurn(
                fixture.adventure().id().value(), commandId, fixture.adventure().version(),
                new AdventureController.GmTurnRequest(turnId,
                        new AdventureController.GmInputRequest("TEXT", "짧은 휴식을 취하겠습니다.", null, null, null, null)));

        assertEquals(org.springframework.http.HttpStatus.ACCEPTED, response.getStatusCode());
        verify(fixture.combatLifecycle(), never()).startFromCommittedGmTurn(any(), any(), any());
        verify(fixture.scheduler(), never()).scheduleNext(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
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
        var combatLifecycle = mock(com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService.class);

        RuntimeTurnRepository runtimeTurnRepository = mock(RuntimeTurnRepository.class);
        AdventureController adventureController = new AdventureController(
                mock(com.dndmaster.adventure.application.saved.SavedAdventureApplicationService.class), runtimeTurns,
                adventures, gmTurnFailures, gmTurns,
                runtimeTurnRepository, sessionEvents,
                mock(com.dndmaster.adventure.application.guidance.RuleGuidanceApplicationService.class),
                mock(AdventureCombatApplicationService.class), combatActions,
                mock(com.dndmaster.adventure.application.scenario.AdventureScenarioApplicationService.class),
                playerResolver,
                provider(mock(com.dndmaster.adventure.application.combat.CombatMapPort.class)),
                provider(mock(com.dndmaster.adventure.application.combat.SpatialActionAuthorizationPort.class)),
                provider(mock(com.dndmaster.adventure.application.combat.CharacterCombatPort.class)),
                new ObjectMapper(),
                provider(mock(com.dndmaster.adventure.application.combat.CombatMapViewPort.class)),
                provider(mock(com.dndmaster.adventure.application.combat.CombatMapPreparationPort.class)),
                mock(com.dndmaster.adventure.application.combat.PendingMapMovementConfirmationRepository.class),
                mock(com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository.class),
                combatLifecycle,
                mock(com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService.class), aiRequests);
        CombatController combatController = new CombatController(
                encounters, playerResolver,
                adventures, mock(com.dndmaster.adventure.application.combat.CombatEventRepository.class), combatActions,
                mock(com.dndmaster.adventure.application.combat.CombatReactionApplicationService.class),
                workItems, scheduler,
                mock(com.dndmaster.adventure.application.combat.CharacterCombatPort.class), aiRequests);
        return new Fixture(adventure, adventureController, combatController, aiRequests, gmTurns, runtimeTurns, runtimeTurnRepository,
                combatActions, encounters, workItems, scheduler, gmTurnFailures, sessionEvents, combatLifecycle);
    }

    private static <T> ObjectProvider<T> provider(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getIfAvailable(Supplier<T> defaultSupplier) {
                return value;
            }
        };
    }

    private record Fixture(Adventure adventure, AdventureController adventureController,
            CombatController combatController, AdventureAiRequestApplicationService aiRequests,
            GmTurnRepository gmTurns, RuntimeTurnApplicationService runtimeTurns,
            RuntimeTurnRepository runtimeTurnRepository,
            CombatActionApplicationService combatActions,
            com.dndmaster.adventure.application.combat.CombatEncounterRepository encounters,
            CombatWorkItemRepository workItems, CombatWorkItemScheduler scheduler,
            com.dndmaster.adventure.application.runtime.GmTurnFailureRecorder gmTurnFailures,
            com.dndmaster.adventure.application.runtime.SessionEventRepository sessionEvents,
            com.dndmaster.adventure.application.combat.CombatLifecycleApplicationService combatLifecycle) { }
}
