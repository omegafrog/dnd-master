package com.dndmaster.adventure.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.combat.AiCombatPort;
import com.dndmaster.adventure.application.combat.CharacterCombatPort;
import com.dndmaster.adventure.application.combat.CombatActionApplicationService;
import com.dndmaster.adventure.application.combat.CombatActionCommand;
import com.dndmaster.adventure.application.combat.CombatActionOperation;
import com.dndmaster.adventure.application.combat.CombatActionOperationRepository;
import com.dndmaster.adventure.application.combat.CombatActionResponse;
import com.dndmaster.adventure.application.combat.CombatActorRole;
import com.dndmaster.adventure.application.combat.CombatEncounterRepository;
import com.dndmaster.adventure.application.combat.CombatEventRepository;
import com.dndmaster.adventure.application.combat.CombatMapMoveCommand;
import com.dndmaster.adventure.application.combat.CombatMapMoveResult;
import com.dndmaster.adventure.application.combat.CombatMapMovementStatus;
import com.dndmaster.adventure.application.combat.CombatMapPort;
import com.dndmaster.adventure.application.combat.CombatMapPreviewPosition;
import com.dndmaster.adventure.application.runtime.CombatContinuationCommand;
import com.dndmaster.adventure.application.runtime.MovementFollowUpPort;
import com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer;
import com.dndmaster.adventure.application.runtime.PostgresRuntimeContinuationCommandOutcomePort;
import com.dndmaster.adventure.application.runtime.RuntimeCheckProposal;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationCommandPort;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationPort;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandRepository;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.combat.CombatEncounter;
import com.dndmaster.adventure.domain.combat.CombatEvent;
import com.dndmaster.adventure.domain.combat.CombatParticipant;
import com.dndmaster.adventure.domain.combat.CombatStartPolicy;
import com.dndmaster.adventure.domain.combat.TurnResources;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class CombatActionMovementFollowUpTest {
    private String previousDiagnosticsFlag;
    private Logger diagnosticsLogger;
    private ListAppender<ILoggingEvent> diagnosticEvents;

    @AfterEach
    void restore_diagnostic_logging() {
        if (diagnosticsLogger != null && diagnosticEvents != null) diagnosticsLogger.detachAppender(diagnosticEvents);
        if (previousDiagnosticsFlag == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", previousDiagnosticsFlag);
    }

    @Test
    void action_submission_reuses_committed_movement_and_saved_typed_result_after_lost_ack() {
        previousDiagnosticsFlag = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "false");
        diagnosticsLogger = (Logger) LoggerFactory.getLogger(CombatActionApplicationService.class);
        diagnosticEvents = new ListAppender<>();
        diagnosticEvents.start();
        diagnosticsLogger.addAppender(diagnosticEvents);
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        UUID goblinId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(goblinId, "Goblin", CombatParticipant.Controller.AI, 10, null)));
        EncounterStore encounters = new EncounterStore(encounter);
        OperationStore operations = new OperationStore();
        EventStore combatEvents = new EventStore();
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger mapMoves = new AtomicInteger();
        AtomicInteger mapQueries = new AtomicInteger();
        AtomicInteger adjudications = new AtomicInteger();
        AtomicInteger failedFinalRulings = new AtomicInteger();
        List<UUID> adjudicatedTurnIds = new ArrayList<>();
        UUID[] storedMapOperationId = new UUID[1];
        CombatMapPort maps = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand ignored) { throw new AssertionError(); }
            @Override public CombatMapMoveResult move(CombatMapMoveCommand command) {
                mapMoves.incrementAndGet();
                storedMapOperationId[0] = command.action().operationId();
                return interrupted(storedMapOperationId[0]);
            }
            @Override public CombatMapMoveResult movementOperation(UUID ignoredMapId, UUID operationId) {
                mapQueries.incrementAndGet();
                assertEquals(storedMapOperationId[0], operationId);
                return interrupted(operationId);
            }
        };
        SessionEventStore sessionEvents = new SessionEventStore();
        RuntimeCommandStore runtimeCommands = new RuntimeCommandStore();
        AtomicInteger publisherAttempts = new AtomicInteger();
        MovementFollowUpPort publisher = (followUp, ignoredAdventureId, ignoredSessionId, ignoredOwnerId) -> {
            if (publisherAttempts.getAndIncrement() == 0) throw new SimulatedProcessInterruption();
            try {
                sessionEvents.append(new SessionEvent(sessionId, followUp.commandId(), 0,
                        "MOVEMENT_FOLLOW_UP", mapper.writeValueAsString(followUp)));
                return MovementFollowUpPort.Result.done("saved");
            } catch (Exception failure) {
                return MovementFollowUpPort.Result.retry(failure.getClass().getSimpleName());
            }
        };
        PostgresRuntimeContinuationCommandOutcomePort typedOutcomes = new PostgresRuntimeContinuationCommandOutcomePort(
                runtimeCommands, mapper, request -> {
                    int attempt = adjudications.incrementAndGet();
                    adjudicatedTurnIds.add(request.continuation().turnId());
                    if (attempt == 1) throw new IllegalStateException("temporary adjudication failure");
                    if (request.continuation().playerRoll() != null) {
                        if (failedFinalRulings.getAndIncrement() == 0) {
                            throw new IllegalStateException("temporary final ruling failure");
                        }
                        return new CombatContinuationCommand(request.command().commandId(), request.command().turnId(),
                                request.continuation().operationId(), request.continuation().hostileTokenId(),
                                request.continuation().trigger(), request.continuation().kind(),
                                "The check is resolved.", "The character may continue their turn.",
                                RuntimeCheckProposal.none(), false, false);
                    }
                    RuntimeCheckProposal requiredPlayerInput = new RuntimeCheckProposal(true,
                            "GM needs the player’s existing check result", "Athletics", heroId,
                            RuntimeCheckProposal.RollMethod.PLAYER, "1d20", 2, 12, List.of("book:movement-check"),
                            "The route continues", "GM_SECRET_NARRATION_MARKER");
                    return new CombatContinuationCommand(request.command().commandId(), request.command().turnId(),
                            request.continuation().operationId(), request.continuation().hostileTokenId(),
                            request.continuation().trigger(), request.continuation().kind(),
                            "The spatial feature needs a decision.",
                            "The character remains at the interruption point.", requiredPlayerInput, false);
                });
        RuntimeContinuationPort continuation = (runtimeCommand, typedRequest) -> {
            try {
                CombatContinuationCommand result = typedOutcomes.combat(
                        new RuntimeContinuationCommandPort.ContinuationCommand(runtimeCommand, typedRequest));
                return com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome.applied(
                        mapper.writeValueAsString(result));
            } catch (Exception failure) {
                return com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome.retry(
                        failure.getClass().getSimpleName());
            }
        };
        MovementFollowUpRuntimeConsumer consumer = new MovementFollowUpRuntimeConsumer(sessionEvents, runtimeCommands,
                mapper, continuation);
        CombatActionApplicationService initialService = movementService(encounters, operations, combatEvents, maps,
                publisher, consumer, runtimeCommands, mapper);
        CombatActionCommand action = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), mapId, CombatActorRole.PLAYER,
                "MOVE", "1,1;2,1", ownerId, UUID.randomUUID(), 1, null, null, null, null, false,
                null, null, 8L);

        assertThrows(SimulatedProcessInterruption.class, () -> initialService.submit(action));
        assertTrue(diagnosticEvents.list.stream().noneMatch(event -> event.getFormattedMessage().startsWith("dev_")),
                "disabled diagnostics must not emit detailed movement records");
        CombatActionOperation committedBeforeInterruption = operations.findByCommandId(action.operationId()).orElseThrow();
        assertEquals(CombatActionOperation.Status.GM_INTERVENTION_PENDING, committedBeforeInterruption.status());
        assertNotNull(committedBeforeInterruption.response());
        assertEquals(null, committedBeforeInterruption.response().movementAdjudication());
        assertEquals(1, mapMoves.get());
        assertEquals(2L, encounters.value.version());
        assertEquals(heroId, encounters.value.currentParticipantId());
        CombatActionCommand competingAction = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), mapId, CombatActorRole.PLAYER,
                "MOVE", "2,1;3,1", ownerId, UUID.randomUUID(), 2, null, null, null, null, false,
                null, null, 9L);
        var blocked = assertThrows(com.dndmaster.adventure.application.combat.CombatCommandRejectedException.class,
                () -> initialService.submit(competingAction));
        assertEquals("COMBAT_ACTION_RECOVERY_PENDING", blocked.violations().getFirst());
        assertEquals(1, mapMoves.get());
        var resourcesBeforeBlockedTurnEnd = encounters.value.currentParticipant().resources();
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        var endTurnWhileRecoveryPending = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), mapId, CombatActorRole.PLAYER,
                "END_TURN", "", ownerId, UUID.randomUUID(), 2, null, null, null, null, false,
                null, null, 9L);
        var blockedTurnEnd = assertThrows(com.dndmaster.adventure.application.combat.CombatCommandRejectedException.class,
                () -> initialService.endTurn(endTurnWhileRecoveryPending));
        assertEquals("COMBAT_ACTION_RECOVERY_PENDING", blockedTurnEnd.violations().getFirst());
        assertEquals(2L, encounters.value.version());
        assertEquals(heroId, encounters.value.currentParticipantId());
        assertEquals(resourcesBeforeBlockedTurnEnd, encounters.value.currentParticipant().resources());
        assertEquals(1, mapMoves.get());

        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        CombatActionApplicationService firstRecoveryInstance = movementService(encounters, operations, combatEvents, maps,
                publisher, consumer, runtimeCommands, mapper);
        assertThrows(IllegalStateException.class, () -> firstRecoveryInstance.submit(action));

        runtimeCommands.failAfterNextContinuationDoneSave = true;
        CombatActionApplicationService interruptedAfterTypedResult = movementService(encounters, operations, combatEvents,
                maps, publisher, consumer, runtimeCommands, mapper);
        assertThrows(SimulatedProcessInterruption.class, () -> interruptedAfterTypedResult.submit(action));
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, runtimeCommands.findByTurnId(adjudicatedTurnIds.getFirst())
                .stream().filter(command -> command.commandType().startsWith("movement.continuation."))
                .findFirst().orElseThrow().executionStatus());

        CombatActionApplicationService service = movementService(encounters, operations, combatEvents, maps,
                publisher, consumer, runtimeCommands, mapper);
        CombatActionResponse pending = service.submit(action);
        CombatActionOperation pendingOperation = operations.findByCommandId(action.operationId()).orElseThrow();
        assertEquals(CombatActionOperation.Status.GM_INTERVENTION_PENDING, pendingOperation.status());
        assertTrue(pending.movementAdjudication().waitingForPlayerInput());
        assertEquals(2, adjudications.get());
        assertEquals(2L, encounters.value.version());
        assertEquals(heroId, encounters.value.currentParticipantId());

        var pendingTurnEnd = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), mapId, CombatActorRole.PLAYER,
                "END_TURN", "", ownerId, UUID.randomUUID(), 2, null, null, null, null, false,
                null, null, 9L);
        assertEquals("COMBAT_ACTION_RECOVERY_PENDING", assertThrows(
                com.dndmaster.adventure.application.combat.CombatCommandRejectedException.class,
                () -> service.endTurn(pendingTurnEnd)).violations().getFirst());
        assertThrows(com.dndmaster.adventure.application.combat.CombatExternalFailureException.class,
                () -> service.submitMovementPlayerRoll(adventureId, action.operationId(),
                        pending.movementAdjudication().turnId(), 14, encounters.value.version()));
        assertEquals(CombatActionOperation.Status.GM_INTERVENTION_PENDING,
                operations.findByCommandId(action.operationId()).orElseThrow().status());
        assertEquals(14, operations.findByCommandId(action.operationId()).orElseThrow().diceTotal());

        CombatActionResponse recovered = service.submitMovementPlayerRoll(adventureId, action.operationId(),
                pending.movementAdjudication().turnId(), 14, encounters.value.version());
        CombatActionResponse replayed = service.submitMovementPlayerRoll(adventureId, action.operationId(),
                pending.movementAdjudication().turnId(), 14, encounters.value.version());

        assertEquals(recovered, replayed);
        assertEquals("MOVE_COMMITTED", recovered.status());
        assertNotNull(recovered.movementAdjudication());
        assertEquals("SPATIAL_FEATURE_REQUIRES_DECISION", recovered.movementAdjudication().trigger());
        assertFalse(recovered.movementAdjudication().playerInput().required());
        assertFalse(recovered.movementAdjudication().waitingForPlayerInput());
        assertFalse(recovered.movementAdjudication().combatStartRequested());
        assertTrue(recovered.movementAdjudication().narration().contains("may continue their turn"));
        assertEquals(4, adjudications.get());
        assertEquals(6, publisherAttempts.get());
        assertEquals(1, adjudicatedTurnIds.stream().distinct().count());
        assertEquals(UUID.nameUUIDFromBytes(("combat-turn:" + encounter.encounterId() + ":"
                + encounter.round() + ":" + heroId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                adjudicatedTurnIds.getFirst());
        assertEquals(1, mapMoves.get());
        assertEquals(3, mapQueries.get());
        assertEquals(2L, encounters.value.version());
        assertEquals(heroId, encounters.value.currentParticipantId());
        assertEquals(CombatActionOperation.Status.COMMITTED,
                operations.findByCommandId(action.operationId()).orElseThrow().status());
        assertEquals(storedMapOperationId[0], operations.findByCommandId(action.operationId()).orElseThrow().mapOperationId());

        List<String> diagnosticMessages = diagnosticEvents.list.stream()
                .map(ILoggingEvent::getFormattedMessage).toList();
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_movement_follow_up")
                && message.contains("outcome=started") && message.contains("operationId=" + storedMapOperationId[0])
                && message.contains("turnId=" + adjudicatedTurnIds.getFirst())));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_movement_follow_up")
                && message.contains("outcome=failed") && message.contains("recoveryAction=retry_same_operation")
                && message.contains("expected=typed_gm_response actual=failed")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_movement_follow_up")
                && message.contains("outcome=completed") && message.contains("expected=typed_gm_response actual=validated")
                && message.contains("recoveryAction=replay_saved_response")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_pending_guard")
                && message.contains("commandId=" + endTurnWhileRecoveryPending.operationId())
                && message.contains("expected=allow_turn_end actual=retry_required")
                && message.contains("recoveryAction=resume_same_pending_operation")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_saved_map_recovery")
                && message.contains("outcome=started") && message.contains("commandId=" + action.operationId())
                && message.contains("operationId=" + storedMapOperationId[0])
                && message.contains("expected=saved_map_result actual=querying")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_saved_map_recovery")
                && message.contains("outcome=completed") && message.contains("expected=GM_INTERVENTION_PENDING actual=preserved")
                && message.contains("recoveryAction=resume_same_operation")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_movement_player_roll")
                && message.contains("outcome=failed") && message.contains("turnId=" + adjudicatedTurnIds.getFirst())
                && message.contains("expected=final_ruling_and_follow_up_saved actual=failed")
                && message.contains("recoveryAction=retry_same_operation")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.contains("operation=combat_action_movement_player_roll")
                && message.contains("outcome=completed") && message.contains("expected=final_ruling_and_follow_up_saved actual=committed")
                && message.contains("recoveryAction=replay_final_response")));
        assertTrue(diagnosticMessages.stream().noneMatch(message -> message.contains("GM_SECRET_NARRATION_MARKER")
                || message.contains("GM needs the player’s existing check result")),
                "diagnostics must not contain GM prose or player-facing input text");
    }

    @Test
    void action_entry_recovers_after_map_result_is_saved_before_encounter_budget_commit() {
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        UUID goblinId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(goblinId, "Goblin", CombatParticipant.Controller.AI, 10, null)));
        EncounterStore encounters = new EncounterStore(encounter);
        OperationStore operations = new OperationStore();
        EventStore events = new EventStore();
        AtomicInteger mapMoves = new AtomicInteger();
        AtomicInteger mapQueries = new AtomicInteger();
        UUID[] mapOperationId = new UUID[1];
        CombatMapPort maps = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand ignored) { throw new AssertionError(); }
            @Override public CombatMapMoveResult move(CombatMapMoveCommand command) {
                mapMoves.incrementAndGet();
                mapOperationId[0] = command.action().operationId();
                return interrupted(mapOperationId[0], "SPATIAL_FEATURE_DISCOVERED");
            }
            @Override public CombatMapMoveResult movementOperation(UUID ignoredMapId, UUID operationId) {
                mapQueries.incrementAndGet();
                assertEquals(mapOperationId[0], operationId);
                return interrupted(operationId, "SPATIAL_FEATURE_DISCOVERED");
            }
        };
        CombatActionCommand action = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), mapId, CombatActorRole.PLAYER,
                "MOVE", "1,1;2,1", ownerId, UUID.randomUUID(), 1, null, null, null, null, false,
                null, null, 8L);
        operations.failAfterNextMapStepSave = true;
        CombatActionApplicationService first = new CombatActionApplicationService(encounters, operations, events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12, usableCharacter(), ai(), maps);

        assertThrows(SimulatedProcessInterruption.class, () -> first.submit(action));
        assertEquals(1, mapMoves.get());
        assertEquals(1L, encounters.value.version());
        CombatActionOperation interrupted = operations.findByCommandId(action.operationId()).orElseThrow();
        assertEquals(com.dndmaster.adventure.application.combat.CombatActionStep.Status.DONE, interrupted.step("map").status());

        CombatActionResponse recovered = new CombatActionApplicationService(encounters, operations, events,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12, usableCharacter(), ai(), maps)
                .submit(action);

        assertEquals("MOVE_COMMITTED", recovered.status());
        assertEquals(1, mapMoves.get());
        assertEquals(1, mapQueries.get());
        assertEquals(2L, encounters.value.version());
        assertEquals(5, encounters.value.currentParticipant().resources().movementUsed());
        assertEquals(mapOperationId[0], operations.findByCommandId(action.operationId()).orElseThrow().mapOperationId());
    }

    @Test
    void action_movement_reuses_saved_map_result_after_encounter_commit_fails() {
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        UUID goblinId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(goblinId, "Goblin", CombatParticipant.Controller.AI, 10, null)));
        EncounterStore encounters = new EncounterStore(encounter);
        OperationStore operations = new OperationStore();
        EventStore combatEvents = new EventStore();
        AtomicInteger mapMoves = new AtomicInteger();
        AtomicInteger mapQueries = new AtomicInteger();
        UUID[] savedMapOperationId = new UUID[1];
        CombatMapPort maps = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand ignored) { throw new AssertionError(); }
            @Override public CombatMapMoveResult move(CombatMapMoveCommand command) {
                mapMoves.incrementAndGet();
                savedMapOperationId[0] = command.action().operationId();
                return interrupted(savedMapOperationId[0], "SPATIAL_FEATURE_DISCOVERED");
            }
            @Override public CombatMapMoveResult movementOperation(UUID ignoredMapId, UUID operationId) {
                mapQueries.incrementAndGet();
                assertEquals(savedMapOperationId[0], operationId);
                return interrupted(operationId, "SPATIAL_FEATURE_DISCOVERED");
            }
        };
        CombatActionApplicationService service = new CombatActionApplicationService(encounters, operations, combatEvents,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12, usableCharacter(), ai(), maps);
        CombatActionCommand action = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), mapId, CombatActorRole.PLAYER,
                "MOVE", "1,1;2,1", ownerId, UUID.randomUUID(), 1, null, null, null, null, false,
                null, null, 8L);
        encounters.failNextSave = true;

        assertThrows(RuntimeException.class, () -> service.submit(action));
        var savedOperation = operations.findByCommandId(action.operationId()).orElseThrow();
        assertEquals(CombatActionOperation.Status.PROCESSING_FAILED, savedOperation.status());
        assertEquals(com.dndmaster.adventure.application.combat.CombatActionStep.Status.DONE,
                savedOperation.step("map").status());
        assertEquals(null, savedOperation.response());
        assertEquals(1, mapMoves.get());
        assertEquals(1L, encounters.value.version());

        CombatActionResponse recovered = new CombatActionApplicationService(encounters, operations, combatEvents,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12, usableCharacter(), ai(), maps)
                .submit(action);

        assertEquals("MOVE_COMMITTED", recovered.status());
        assertEquals(1, mapMoves.get());
        assertEquals(1, mapQueries.get());
        assertEquals(2L, encounters.value.version());
        assertEquals(5, encounters.value.currentParticipant().resources().movementUsed());
    }

    @Test
    void discovered_spatial_feature_interruption_does_not_enqueue_an_unrecognized_gm_trigger() {
        previousDiagnosticsFlag = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        diagnosticsLogger = (Logger) LoggerFactory.getLogger(CombatActionApplicationService.class);
        diagnosticEvents = new ListAppender<>();
        diagnosticEvents.start();
        diagnosticsLogger.addAppender(diagnosticEvents);
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID heroId = UUID.randomUUID();
        UUID goblinId = UUID.randomUUID();
        UUID mapId = UUID.randomUUID();
        CombatEncounter encounter = CombatStartPolicy.startFromCommittedGmTurn(true, adventureId, List.of(
                new CombatParticipant(heroId, "Hero", CombatParticipant.Controller.PLAYER, 15, "healthy"),
                new CombatParticipant(goblinId, "Goblin", CombatParticipant.Controller.AI, 10, null)));
        EncounterStore encounters = new EncounterStore(encounter);
        OperationStore operations = new OperationStore();
        EventStore combatEvents = new EventStore();
        UUID[] savedMapOperationId = new UUID[1];
        CombatMapPort maps = new CombatMapPort() {
            @Override public void validateAndMove(CombatActionCommand ignored) { throw new AssertionError(); }
            @Override public CombatMapMoveResult move(CombatMapMoveCommand command) {
                savedMapOperationId[0] = command.action().operationId();
                return interrupted(savedMapOperationId[0], "SPATIAL_FEATURE_DISCOVERED");
            }
            @Override public CombatMapMoveResult movementOperation(UUID ignoredMapId, UUID operationId) {
                assertEquals(savedMapOperationId[0], operationId);
                return interrupted(operationId, "SPATIAL_FEATURE_DISCOVERED");
            }
        };
        CombatActionApplicationService service = new CombatActionApplicationService(encounters, operations, combatEvents,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12, usableCharacter(), ai(), maps);
        CombatActionCommand action = new CombatActionCommand(UUID.randomUUID(), new AdventureId(adventureId), sessionId,
                new RuleSetId(UUID.randomUUID()), new CharacterSheetId(heroId), mapId, CombatActorRole.PLAYER,
                "MOVE", "1,1;2,1", ownerId, UUID.randomUUID(), 1, null, null, null, null, false,
                null, null, 8L);

        CombatActionResponse response = service.submit(action);

        assertEquals("MOVE_COMMITTED", response.status());
        assertEquals(null, response.movementAdjudication());
        assertEquals(CombatActionOperation.Status.COMMITTED,
                operations.findByCommandId(action.operationId()).orElseThrow().status());
        assertEquals(2L, encounters.value.version());
        assertEquals(heroId, encounters.value.currentParticipantId());
        assertEquals(5, encounters.value.currentParticipant().resources().movementUsed());
        String diagnostic = diagnosticEvents.list.stream().map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("operation=combat_action_unsupported_movement_trigger"))
                .findFirst().orElseThrow();
        assertTrue(diagnostic.contains("commandId=" + action.operationId()));
        assertTrue(diagnostic.contains("operationId=" + savedMapOperationId[0]));
        assertTrue(diagnostic.contains("expected=HOSTILE_OBSERVED_or_SPATIAL_FEATURE_REQUIRES_DECISION"));
        assertTrue(diagnostic.contains("actual=unsupported_or_absent"));
        assertTrue(diagnostic.contains("branch=keep_existing_response"));
        assertTrue(diagnostic.contains("recoveryAction=return_saved_response"));
        assertFalse(diagnostic.contains("TRAP_DISCOVERED:2,1"));
    }

    private static CombatMapMoveResult interrupted(UUID operationId) {
        List<CombatMapPreviewPosition> traversed = List.of(new CombatMapPreviewPosition(1, 1));
        return new CombatMapMoveResult(9, operationId, CombatMapMovementStatus.INTERRUPTED,
                List.of(new CombatMapPreviewPosition(1, 1), new CombatMapPreviewPosition(2, 1)), traversed,
                traversed.getFirst(), List.of("SPATIAL_FEATURE_REQUIRES_DECISION"), "SPATIAL_FEATURE_REQUIRES_DECISION");
    }

    private static CombatMapMoveResult interrupted(UUID operationId, String reason) {
        List<CombatMapPreviewPosition> traversed = List.of(new CombatMapPreviewPosition(1, 1),
                new CombatMapPreviewPosition(2, 1));
        return new CombatMapMoveResult(9, operationId, CombatMapMovementStatus.INTERRUPTED,
                traversed, traversed, traversed.getLast(), List.of("TRAP_DISCOVERED:2,1"), reason);
    }

    private static CombatActionApplicationService movementService(EncounterStore encounters,
            OperationStore operations, EventStore combatEvents, CombatMapPort maps,
            MovementFollowUpPort publisher, MovementFollowUpRuntimeConsumer consumer,
            RuntimeCommandStore runtimeCommands, ObjectMapper mapper) {
        CombatActionApplicationService service = new CombatActionApplicationService(encounters, operations, combatEvents,
                new com.dndmaster.adventure.domain.combat.CombatRulesEngine(), ignored -> 12, usableCharacter(), ai(), maps);
        service.setMovementFollowUpPorts(publisher, consumer, runtimeCommands, mapper);
        return service;
    }

    private static CharacterCombatPort usableCharacter() {
        return new CharacterCombatPort() {
            @Override public void requireUsableCharacter(CombatActionCommand ignored) { }
            @Override public void applyOutcome(CombatActionCommand ignored,
                    com.dndmaster.adventure.application.combat.CombatOutcome ignoredOutcome) { }
        };
    }

    private static AiCombatPort ai() {
        return new AiCombatPort() {
            @Override public void controlState(CombatActionCommand ignored) { }
            @Override public String adjudicate(CombatActionCommand ignored, int diceTotal) { return "hit"; }
        };
    }

    private static final class EncounterStore implements CombatEncounterRepository {
        private CombatEncounter value;
        private boolean failNextSave;
        private EncounterStore(CombatEncounter value) { this.value = value; }
        @Override public Optional<CombatEncounter> findActive(UUID ignored) { return Optional.of(value); }
        @Override public Optional<CombatEncounter> findByEncounterId(UUID id) {
            return value.encounterId().equals(id) ? Optional.of(value) : Optional.empty();
        }
        @Override public CombatEncounter save(CombatEncounter encounter) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("simulated interruption before encounter save");
            }
            value = encounter;
            return encounter;
        }
    }

    private static final class SimulatedProcessInterruption extends Error { }

    private static final class OperationStore implements CombatActionOperationRepository {
        private final Map<UUID, CombatActionOperation> values = new HashMap<>();
        private boolean failAfterNextMapStepSave;
        @Override public Optional<CombatActionOperation> findByCommandId(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public void save(CombatActionOperation operation) {
            values.put(operation.commandId(), operation);
            if (failAfterNextMapStepSave && operation.step("map") != null
                    && operation.step("map").status() == com.dndmaster.adventure.application.combat.CombatActionStep.Status.DONE) {
                failAfterNextMapStepSave = false;
                throw new SimulatedProcessInterruption();
            }
        }
        @Override public boolean hasPendingForEncounter(UUID encounterId) {
            return values.values().stream().anyMatch(operation -> operation.encounterId().equals(encounterId)
                    && operation.status() != CombatActionOperation.Status.COMMITTED);
        }
    }

    private static final class EventStore implements CombatEventRepository {
        private final List<CombatEvent> events = new ArrayList<>();
        @Override public void append(CombatEvent event) { events.add(event); }
        @Override public List<CombatEvent> after(UUID ignored, long sequence) { return List.copyOf(events); }
    }

    private static final class SessionEventStore implements SessionEventRepository {
        private final List<SessionEvent> events = new ArrayList<>();
        @Override public void append(SessionEvent event) {
            if (events.stream().noneMatch(saved -> saved.sessionId().equals(event.sessionId())
                    && saved.eventId().equals(event.eventId()))) events.add(event);
        }
        @Override public List<SessionEvent> after(UUID sessionId, long version) {
            return events.stream().filter(event -> event.sessionId().equals(sessionId) && event.version() > version).toList();
        }
    }

    private static final class RuntimeCommandStore implements RuntimeTurnCommandRepository {
        private final Map<UUID, RuntimeTurnCommand> values = new HashMap<>();
        private boolean failAfterNextContinuationDoneSave;
        @Override public Optional<RuntimeTurnCommand> findByCommandId(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public List<RuntimeTurnCommand> findByTurnId(UUID turnId) {
            return values.values().stream().filter(command -> command.turnId().equals(turnId)).toList();
        }
        @Override public void save(RuntimeTurnCommand command) {
            values.put(command.commandId(), command);
            if (failAfterNextContinuationDoneSave && command.commandType().startsWith("movement.continuation.")
                    && command.executionStatus() == RuntimeTurnCommand.ExecutionStatus.DONE) {
                failAfterNextContinuationDoneSave = false;
                throw new SimulatedProcessInterruption();
            }
        }
    }
}
