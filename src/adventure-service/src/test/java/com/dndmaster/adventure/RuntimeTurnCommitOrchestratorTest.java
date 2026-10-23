package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;

import com.dndmaster.adventure.application.runtime.InMemoryRuntimeTurnCommandRepository;
import com.dndmaster.adventure.application.runtime.InMemorySessionEventRepository;
import com.dndmaster.adventure.application.runtime.MovementFollowUpEventPublisher;
import com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer;
import com.dndmaster.adventure.application.runtime.MovementFollowUpPort;
import com.dndmaster.adventure.application.runtime.InMemoryRuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.RuntimeTurn;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandAdapter;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommitOrchestrator;
import com.dndmaster.adventure.application.runtime.RuntimeTurnLifecycle;
import com.dndmaster.adventure.application.runtime.RuntimeTurnRepository;
import com.dndmaster.adventure.application.runtime.NarrationSafetyPort;
import com.dndmaster.adventure.application.runtime.RuntimeBindingRepository;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceSearchPort;
import com.dndmaster.adventure.application.runtime.RuntimePlanningPort;
import com.dndmaster.adventure.application.runtime.RuntimeTurnApplicationService;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;

class RuntimeTurnCommitOrchestratorTest {
    @Test
    void malformed_gm_response_logs_expected_and_actual_schema_without_response_values() throws Exception {
        String priorDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        Logger diagnosticLogger = (Logger) LoggerFactory.getLogger(MovementFollowUpRuntimeConsumer.class);
        ListAppender<ILoggingEvent> diagnostics = new ListAppender<>();
        diagnostics.start();
        diagnosticLogger.addAppender(diagnostics);
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID turnId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var source = RuntimeTurnCommand.create(turnId, UUID.randomUUID(), adventureId, sessionId, ownerId,
                "external", "combat-map.move", "{}", 0).done("{}");
        var followUp = com.dndmaster.adventure.application.combat.MovementFollowUpCommand.hostileObserved(
                operationId, turnId, hostileTokenId);
        UUID continuationId = UUID.nameUUIDFromBytes(("movement-continuation:" + followUp.commandId())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fixture.followUpPublisher().publish(followUp, adventureId, sessionId, ownerId);
        var consumer = new MovementFollowUpRuntimeConsumer(fixture.events, fixture.commands, fixture.objectMapper,
                (command, continuation) -> com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome
                        .applied("{\"privateMarker\":\"never-log-this-value\"}"));
        try {
            var result = consumer.consume(source, followUp);
            assertEquals(MovementFollowUpPort.Result.Status.PERMANENT_FAILURE, result.status());
            String log = diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(log.contains("operation=runtime_movement_gm_response_schema")
                    && log.contains("commandId=" + continuationId) && log.contains("turnId=" + turnId)
                    && log.contains("expectedStructure=CombatContinuationCommand")
                    && log.contains("actualStructure=root=OBJECT") && log.contains("missing=[commandId")
                    && log.contains("recoveryAction=retain_pending_same_command"));
            assertTrue(!log.contains("never-log-this-value"));
        } finally {
            diagnosticLogger.detachAppender(diagnostics);
            if (priorDiagnostics == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", priorDiagnostics);
        }
    }

    @Test
    void gm_adjudication_diagnostics_identify_failed_durable_work_without_prompt_text() {
        String priorDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        Logger diagnosticLogger = (Logger) LoggerFactory.getLogger(RuntimeTurnApplicationService.class);
        ListAppender<ILoggingEvent> diagnostics = new ListAppender<>();
        diagnostics.start();
        diagnosticLogger.addAppender(diagnostics);
        UUID turnId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID adventureId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(turnId, commandId, adventureId, sessionId, ownerId,
                "external", "movement.continuation.combat", "{\"privateMarker\":\"never-log-me\"}", 1);
        var continuation = new com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer.Continuation(
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT, "HOSTILE_OBSERVED",
                operationId, turnId, UUID.randomUUID(), 4, List.of(), null, List.of("HOSTILE_OBSERVED"), null, null);
        var service = new RuntimeTurnApplicationService(mock(AdventureRepository.class), mock(RuntimeBindingRepository.class),
                mock(ScenarioPackageRepository.class), mock(RuntimeTurnRepository.class), mock(RuntimeEvidenceSearchPort.class),
                mock(RuntimePlanningPort.class), mock(NarrationSafetyPort.class), mock(SessionKnowledgeSetRepository.class));
        try {
            assertThrows(IllegalStateException.class, () -> service.adjudicateMovementFollowUp(
                    new com.dndmaster.adventure.application.runtime.RuntimeContinuationCommandPort.ContinuationCommand(
                            command, continuation)));
            String log = diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(log.contains("operation=combat_movement_gm_adjudication")
                    && log.contains("commandId=" + commandId) && log.contains("turnId=" + turnId)
                    && log.contains("expected=typed_response actual=failed")
                    && log.contains("recoveryAction=retry_same_durable_operation"));
            assertTrue(!log.contains("never-log-me"));
        } finally {
            diagnosticLogger.detachAppender(diagnostics);
            if (priorDiagnostics == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
            else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", priorDiagnostics);
        }
    }

    @Test
    void failed_required_map_entry_keeps_adventure_uncommitted_and_turn_resumable() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        RuntimeTurn ready = fixture.readyTurn();
        fixture.turns.save(ready.beginCommit());
        var adventureRepository = mock(AdventureRepository.class);
        Adventure adventure = mock(Adventure.class);
        when(adventureRepository.findById(ready.adventureId())).thenReturn(Optional.of(adventure));
        when(adventure.version()).thenReturn(ready.version());
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(UUID.randomUUID()));
        var service = new RuntimeTurnApplicationService(adventureRepository, mock(RuntimeBindingRepository.class),
                mock(ScenarioPackageRepository.class), fixture.turns, mock(RuntimeEvidenceSearchPort.class),
                mock(RuntimePlanningPort.class), mock(NarrationSafetyPort.class), mock(SessionKnowledgeSetRepository.class));
        service.setCommitOrchestrator(fixture.orchestrator(ignored -> RuntimeTurnCommandExecution.done("done")));
        service.setCommitGate((pendingAdventure, pendingTurn) -> { throw new IllegalStateException("map spawn needs review"); });

        assertThrows(IllegalStateException.class, () -> service.resumeRuntimeTurn(ready.turnId()));

        assertEquals(RuntimeTurnLifecycle.COMMITTING, fixture.turns.findByTurnId(ready.turnId()).orElseThrow().lifecycle());
        verify(adventure).commitRuntimeTurn(any(OwnerPlayerId.class), eq(ready.version()), eq(ready.pendingState()),
                eq(ready.context()), eq(List.of(
                        new com.dndmaster.adventure.domain.adventure.ConversationEntry(0, "PLAYER", ready.action()),
                        new com.dndmaster.adventure.domain.adventure.ConversationEntry(1, "AI_GAME_MASTER", ready.narration()))),
                eq(ready.completionProposal()));
        verify(adventureRepository, never()).save(adventure);
    }

    @Test
    void repairs_pre_turn_context_and_conversation_after_older_forward_recovery() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        RuntimeTurn committed = fixture.readyTurn().beginCommit().markSafeCommitted();
        fixture.turns.save(committed);
        var adventureRepository = mock(AdventureRepository.class);
        Adventure adventure = mock(Adventure.class);
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        when(adventureRepository.findById(committed.adventureId())).thenReturn(Optional.of(adventure));
        when(adventure.version()).thenReturn(committed.version() + 1);
        when(adventure.ownerPlayerId()).thenReturn(owner);
        when(adventure.currentContext()).thenReturn(committed.context());
        when(adventure.conversation()).thenReturn(committed.conversation());
        when(adventure.currentSituation()).thenReturn(committed.pendingState().situation());
        var service = new RuntimeTurnApplicationService(adventureRepository, mock(RuntimeBindingRepository.class),
                mock(ScenarioPackageRepository.class), fixture.turns, mock(RuntimeEvidenceSearchPort.class),
                mock(RuntimePlanningPort.class), mock(NarrationSafetyPort.class), mock(SessionKnowledgeSetRepository.class));
        service.setCommitOrchestrator(fixture.orchestrator(ignored -> RuntimeTurnCommandExecution.done("done")));

        var recovered = service.resumeRuntimeTurn(committed.turnId());

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, recovered.status());
        verify(adventure).preserveProgress(eq(owner), eq(committed.version() + 1),
                eq(new com.dndmaster.adventure.domain.adventure.AdventureContext(
                        committed.plan().scene(), committed.plan().npcState(), committed.action(), committed.plan().judgment())),
                org.mockito.ArgumentMatchers.argThat(entries -> entries.size() == committed.conversation().size() + 2
                        && entries.get(entries.size() - 2).content().equals(committed.action())
                        && entries.get(entries.size() - 1).content().equals(committed.narration())));
        verify(adventureRepository).save(adventure);
    }

    @Test
    void retains_the_original_raw_follow_up_json_in_the_durable_command() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        UUID followUpId = UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var followUp = new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId, operationId, hostileTokenId, fixture.turnId,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED", 3, List.of(), null, List.of("HOSTILE_OBSERVED"));
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(
                3, operationId, com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED,
                List.of(), List.of(), null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED",
                null, followUp, null, hostileTokenId);
        String rawFollowUp = "{ \"trigger\" : \"HOSTILE_OBSERVED\", \"kind\" : \"COMBAT\","
                + " \"turnId\" : \"" + fixture.turnId + "\", \"hostileTokenId\" : \"" + hostileTokenId
                + "\", \"operationId\" : \"" + operationId + "\", \"commandId\" : \"" + followUpId
                + "\", \"mapVersion\" : 3, \"traversedPath\" : [], \"finalPosition\" : null"
                + ", \"publicEvents\" : [\"HOSTILE_OBSERVED\"] }";
        String rawMovement = "{\"version\":3,\"operationId\":\"" + operationId
                + "\",\"status\":\"INTERRUPTED\",\"requestedPath\":[],\"traversedPath\":[],"
                + "\"finalPosition\":null,\"publicEvents\":[\"HOSTILE_OBSERVED\"],"
                + "\"interruptionReason\":\"HOSTILE_OBSERVED\",\"pendingCheck\":null,\"followUp\":"
                + rawFollowUp + ",\"hostileTokenId\":\"" + hostileTokenId + "\"}";
        RuntimeTurnCommand command = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        var captured = new java.util.concurrent.atomic.AtomicReference<com.dndmaster.adventure.application.combat.MovementFollowUpCommand>();

        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, rawMovement, movement),
                (published, adventureId, sessionId, ownerPlayerId) -> {
                    captured.set(published);
                    return fixture.followUpPublisher().publish(published, adventureId, sessionId, ownerPlayerId);
                }).commit(fixture.readyTurn(), List.of(command), () -> {});

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, result.status(),
                result.failedCommand() == null ? "no failed command" : result.failedCommand().lastError());
        assertEquals(rawFollowUp, fixture.commands.findByCommandId(followUpId).orElseThrow().payloadJson());
        assertEquals(followUp, captured.get());
    }

    @Test
    void rejects_a_mismatched_raw_follow_up_identity_permanently() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var followUp = com.dndmaster.adventure.application.combat.MovementFollowUpCommand.hostileObserved(
                operationId, fixture.turnId, hostileTokenId);
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(
                3, operationId, com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED,
                List.of(), List.of(), null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED",
                null, followUp, null, hostileTokenId);
        String rawMovement = "{\"operationId\":\"" + operationId + "\",\"followUp\":{"
                + "\"commandId\":\"" + UUID.randomUUID() + "\",\"operationId\":\"" + operationId
                + "\",\"hostileTokenId\":\"" + hostileTokenId + "\",\"turnId\":\"" + fixture.turnId
                + "\",\"kind\":\"COMBAT\",\"trigger\":\"HOSTILE_OBSERVED\"}}";
        RuntimeTurnCommand command = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);

        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, rawMovement, movement))
                .commit(fixture.readyTurn(), List.of(command), () -> {});

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
        assertTrue(fixture.commands.findByTurnId(fixture.turnId).stream()
                .anyMatch(saved -> saved.executionStatus() == RuntimeTurnCommand.ExecutionStatus.FAILED));
    }

    @Test
    void rejects_follow_up_command_id_not_derived_from_current_movement_operation() throws Exception {
        UUID operationId = UUID.randomUUID();
        UUID differentOperationId = UUID.randomUUID();
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        assertCorruptFollowUp(fixture, new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                UUID.randomUUID(), operationId, UUID.randomUUID(),
                fixture.turnId, com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED"), operationId);
    }

    @Test
    void rejects_follow_up_operation_id_different_from_current_movement_result() throws Exception {
        UUID operationId = UUID.randomUUID();
        UUID differentOperationId = UUID.randomUUID();
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        assertCorruptFollowUp(fixture, new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId(operationId), differentOperationId, UUID.randomUUID(), fixture.turnId,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED"), operationId);
    }

    @Test
    void rejects_follow_up_hostile_token_id_different_from_current_movement_result() throws Exception {
        UUID operationId = UUID.randomUUID();
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        assertCorruptFollowUp(fixture, new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId(operationId), operationId, UUID.randomUUID(), fixture.turnId,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED"), operationId, UUID.randomUUID());
    }

    @Test
    void rejects_follow_up_trigger_other_than_hostile_observed() throws Exception {
        UUID operationId = UUID.randomUUID();
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        assertCorruptFollowUp(fixture, new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId(operationId), operationId, UUID.randomUUID(), fixture.turnId,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "UNEXPECTED_TRIGGER"), operationId);
    }

    @Test
    void rejects_follow_up_turn_id_different_from_current_movement_command() throws Exception {
        UUID operationId = UUID.randomUUID();
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        assertCorruptFollowUp(fixture, new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId(operationId), operationId, UUID.randomUUID(), UUID.randomUUID(),
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED"), operationId);
    }

    private static UUID followUpId(UUID operationId) {
        return UUID.nameUUIDFromBytes(("movement-follow-up:" + operationId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void assertCorruptFollowUp(RuntimeTurnFixture fixture,
            com.dndmaster.adventure.application.combat.MovementFollowUpCommand followUp,
            UUID movementOperationId) throws Exception {
        assertCorruptFollowUp(fixture, followUp, movementOperationId, followUp.hostileTokenId());
    }

    private static void assertCorruptFollowUp(RuntimeTurnFixture fixture,
            com.dndmaster.adventure.application.combat.MovementFollowUpCommand followUp,
            UUID movementOperationId, UUID movementHostileTokenId) throws Exception {
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(
                3, movementOperationId, com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED,
                List.of(), List.of(), null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED",
                null, followUp, null, movementHostileTokenId);
        RuntimeTurnCommand command = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        AtomicInteger dispatches = new AtomicInteger();
        String movementOutcome = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(movement);
        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE,
                        movementOutcome, movement),
                (published, adventureId, sessionId, ownerPlayerId) -> {
                    dispatches.incrementAndGet();
                    return MovementFollowUpPort.Result.done("must not dispatch");
                }).commit(fixture.readyTurn(), List.of(command), () -> {
                    throw new AssertionError("corrupt follow-up must not commit the adventure");
                });

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
        assertEquals(0, dispatches.get());
    }
    @Test
    void executesCommandsInOrderAndSkipsDoneCommandsOnResume() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        List<String> calls = new ArrayList<>();
        RuntimeTurnCommand first = fixture.command("character.update", 1, RuntimeTurnCommand.ExecutionStatus.DONE);
        RuntimeTurnCommand second = fixture.command("combat-map.update", 2, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommitOrchestrator orchestrator = fixture.orchestrator(command -> {
            calls.add(command.commandType());
            return RuntimeTurnCommandExecution.done("ok");
        });

        RuntimeTurnCommitOrchestrator.Result result = orchestrator.commit(
                fixture.readyTurn(), List.of(first, second), () -> calls.add("adventure"));

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, result.status());
        assertEquals(List.of("combat-map.update", "adventure"), calls);
        assertEquals(RuntimeTurnLifecycle.COMMITTED, fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE,
                fixture.commands.findByTurnId(fixture.turnId).get(1).executionStatus());
    }

    @Test
    void transientFailureLeavesCommittingAndResumeContinuesFromFailedCommand() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        List<String> calls = new ArrayList<>();
        RuntimeTurnCommand first = fixture.command("character.update", 1, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommand second = fixture.command("combat-map.update", 2, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommandAdapter adapter = new RuntimeTurnCommandAdapter() {
            private boolean failed;
            @Override public RuntimeTurnCommandExecution execute(RuntimeTurnCommand command) {
                calls.add(command.commandType());
                if (!failed) {
                    failed = true;
                    return RuntimeTurnCommandExecution.transientFailure("temporary outage");
                }
                return RuntimeTurnCommandExecution.done("ok");
            }
        };
        RuntimeTurnCommitOrchestrator orchestrator = fixture.orchestrator(adapter);

        RuntimeTurnCommitOrchestrator.Result firstAttempt = orchestrator.commit(
                fixture.readyTurn(), List.of(first, second), () -> calls.add("adventure"));
        RuntimeTurnCommitOrchestrator.Result resumed = orchestrator.resume(
                fixture.turnId, () -> calls.add("adventure"));

        assertEquals(RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED, firstAttempt.status());
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, resumed.status());
        assertEquals(List.of("character.update", "character.update", "combat-map.update", "adventure"), calls);
    }

    @Test
    void permanentFailureMarksRepairRequiredAndDoesNotCommitAdventure() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        List<String> calls = new ArrayList<>();
        RuntimeTurnCommand command = fixture.command("combat-map.update", 1, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommitOrchestrator orchestrator = fixture.orchestrator(
                ignored -> RuntimeTurnCommandExecution.permanentFailure("invalid map token"));

        RuntimeTurnCommitOrchestrator.Result result = orchestrator.commit(
                fixture.readyTurn(), List.of(command), () -> calls.add("adventure"));

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
        assertEquals(List.of(), calls);
        assertEquals(RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED,
                fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
        RuntimeTurnCommitOrchestrator.Result resumed = fixture.orchestrator(
                ignored -> RuntimeTurnCommandExecution.done("must not run")).resume(
                        fixture.turnId, () -> calls.add("adventure"));
        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, resumed.status());
    }

    @Test
    void preservesTypedMovementRetryResultInTheDurableCommand() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(3, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.RETRY_REQUIRED,
                List.of(new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1)),
                new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1), List.of(), null);
        String outcome = "{\"operationId\":\"" + operationId + "\",\"status\":\"RETRY_WAIT\"}";
        RuntimeTurnCommand command = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        RuntimeTurnCommitOrchestrator orchestrator = fixture.orchestrator(ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.TRANSIENT_FAILURE, outcome, movement));

        RuntimeTurnCommitOrchestrator.Result result = orchestrator.commit(
                fixture.readyTurn(), List.of(command), () -> {});

        assertEquals(RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED, result.status());
        assertEquals(movement, result.movementResult());
        assertEquals(outcome, fixture.commands.findByCommandId(command.commandId()).orElseThrow().outcomeJson());
    }

    @Test
    void map_ui_resumes_after_the_movement_result_is_saved_without_running_the_map_command_again() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        String outcome = "{\"version\":4,\"operationId\":\"" + operationId
                + "\",\"status\":\"INTERRUPTED\",\"requestedPath\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}],"
                + "\"traversedPath\":[{\"x\":1,\"y\":1}],\"finalPosition\":{\"x\":1,\"y\":1},"
                + "\"publicEvents\":[\"HOSTILE_OBSERVED\"],\"interruptionReason\":\"HOSTILE_OBSERVED\"}";
        RuntimeTurnCommand command = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        fixture.commands.save(command.done(outcome));
        RuntimeTurn ready = fixture.readyTurn();
        fixture.turns.save(ready.beginCommit());

        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored ->
                { throw new AssertionError("a done movement command must not execute again"); })
                .resume(fixture.turnId, () -> { });

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, result.status());
        assertEquals(operationId, result.movementResult().operationId());
        assertEquals(com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED,
                result.movementResult().status());
        assertEquals(List.of(new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1)),
                result.movementResult().traversedPath());
    }

    @Test
    void persists_and_retries_the_idempotent_movement_follow_up_after_restart() throws Exception {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED, List.of(), List.of(),
                null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED", null,
                new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                        followUpId(operationId), operationId, hostileTokenId, fixture.turnId,
                        com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                        "HOSTILE_OBSERVED", 4, List.of(), null, List.of("HOSTILE_OBSERVED")), null, hostileTokenId);
        RuntimeTurnCommand move = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        AtomicInteger publications = new AtomicInteger();
        var durablePublisher = fixture.followUpPublisher();
        var followUpPort = (MovementFollowUpPort)
                (command, adventureId, sessionId, ownerPlayerId) -> publications.getAndIncrement() == 0
                        ? MovementFollowUpPort.Result.retry("downstream unavailable")
                        : durablePublisher.publish(command, adventureId, sessionId, ownerPlayerId);
        String movementOutcome = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(movement);
        var orchestrator = fixture.orchestrator(ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, movementOutcome, movement), followUpPort);

        RuntimeTurnCommitOrchestrator.Result firstAttempt = orchestrator.commit(fixture.readyTurn(), List.of(move), () -> {});
        assertEquals(RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED, firstAttempt.status(),
                firstAttempt.failedCommand() == null ? "no failed command" : firstAttempt.failedCommand().lastError());
        // Simulate a new application instance loading only the saved turn and commands.
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED,
                fixture.orchestrator(ignored -> { throw new AssertionError("saved movement must not run again"); }, followUpPort)
                        .resume(fixture.turnId, () -> {}).status());
        assertEquals(2, publications.get());
        assertEquals(1, fixture.commands.findByTurnId(fixture.turnId).stream()
                .filter(command -> command.commandType().equals("movement.follow-up")).count());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, fixture.commands.findByTurnId(fixture.turnId).stream()
                .filter(command -> command.commandType().equals("movement.follow-up")).findFirst().orElseThrow().executionStatus());
    }

    @Test
    void map_player_roll_is_durably_reused_when_final_ruling_retries() throws Exception {
        String priorDiagnostics = System.getProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true");
        Logger diagnosticLogger = (Logger) LoggerFactory.getLogger(RuntimeTurnCommitOrchestrator.class);
        Level previousLevel = diagnosticLogger.getLevel();
        ListAppender<ILoggingEvent> diagnostics = new ListAppender<>();
        diagnostics.start();
        diagnosticLogger.addAppender(diagnostics);
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        RuntimeTurn ready = fixture.readyTurn();
        fixture.turns.save(ready.beginCommit());
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var check = new com.dndmaster.adventure.application.runtime.RuntimeCheckProposal(true,
                "확인 판정", "Athletics", UUID.randomUUID(),
                com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.RollMethod.PLAYER,
                "1d20", 2, 12, List.of("book:check"), "계속 진행", "다시 시도합니다.");
        var followUp = new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId(operationId), operationId, hostileTokenId, fixture.turnId,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED", 4, List.of(), null, List.of("HOSTILE_OBSERVED"));
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED, List.of(), List.of(),
                null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED", null, followUp, null, hostileTokenId);
        String movementJson = fixture.objectMapper.writeValueAsString(movement);
        fixture.commands.save(fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING)
                .done(movementJson));
        var initialRuling = new com.dndmaster.adventure.application.runtime.CombatContinuationCommand(
                UUID.randomUUID(), fixture.turnId, operationId, hostileTokenId, "HOSTILE_OBSERVED",
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "판정 대기", "굴림을 제출해 주세요.", check, true, false);
        RuntimeTurnCommand pending = RuntimeTurnCommand.create(fixture.turnId, followUp.commandId(),
                ready.adventureId().value(), ready.sessionId(), UUID.randomUUID(), "external",
                "movement.follow-up", fixture.objectMapper.writeValueAsString(followUp), 1)
                .movementInterventionPending(fixture.objectMapper.writeValueAsString(initialRuling));
        fixture.commands.save(pending);
        AtomicInteger finalRulings = new AtomicInteger();
        MovementFollowUpRuntimeConsumer consumer = new MovementFollowUpRuntimeConsumer(fixture.events,
                fixture.commands, fixture.objectMapper, (command, continuation) -> {
                    if (finalRulings.getAndIncrement() == 0)
                        return com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome.retry("temporary final ruling failure");
                    try {
                        return com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome.applied(fixture.objectMapper.writeValueAsString(
                                new com.dndmaster.adventure.application.runtime.CombatContinuationCommand(
                                        command.commandId(), continuation.turnId(), continuation.operationId(),
                                        continuation.hostileTokenId(), continuation.trigger(), continuation.kind(),
                                        "완료", "다음 행동을 선택해 주세요.",
                                        com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.none(), false, false)));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException(failure); }
                });
        RuntimeTurnCommitOrchestrator orchestrator = new RuntimeTurnCommitOrchestrator(fixture.turns,
                fixture.commands, ignored -> { throw new AssertionError("saved map move must not run again"); },
                fixture.followUpPublisher()::publish, consumer);

        assertEquals(RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED, orchestrator.resume(fixture.turnId, () -> {}).status());

        assertEquals(RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED,
                orchestrator.submitMovementPlayerRoll(fixture.turnId, 14).status());
        RuntimeTurnCommand afterFailure = fixture.commands.findByCommandId(followUp.commandId()).orElseThrow();
        var savedRoll = fixture.objectMapper.readValue(afterFailure.payloadJson(),
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.class);
        assertEquals(14, savedRoll.playerRoll());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.GM_INTERVENTION_PENDING, afterFailure.executionStatus());

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED,
                new RuntimeTurnCommitOrchestrator(fixture.turns, fixture.commands,
                        ignored -> { throw new AssertionError("saved map move must not run again"); },
                        fixture.followUpPublisher()::publish, consumer)
                        .submitMovementPlayerRoll(fixture.turnId, 14).status());
        assertEquals(2, finalRulings.get());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE,
                fixture.commands.findByCommandId(followUp.commandId()).orElseThrow().executionStatus());
        assertEquals(14, fixture.objectMapper.readValue(fixture.commands.findByCommandId(followUp.commandId()).orElseThrow().payloadJson(),
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.class).playerRoll());
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED,
                new RuntimeTurnCommitOrchestrator(fixture.turns, fixture.commands,
                        ignored -> { throw new AssertionError("saved map move must not run again"); },
                        fixture.followUpPublisher()::publish, consumer)
                        .submitMovementPlayerRoll(fixture.turnId, 14).status());
        assertEquals(2, finalRulings.get());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new RuntimeTurnCommitOrchestrator(fixture.turns, fixture.commands,
                        ignored -> { throw new AssertionError("saved map move must not run again"); },
                        fixture.followUpPublisher()::publish, consumer)
                        .submitMovementPlayerRoll(fixture.turnId, 13));
        String log = diagnostics.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertTrue(log.contains("operation=runtime_movement_intervention_pending")
                && log.contains("expected=final_ruling_saved actual=GM_INTERVENTION_PENDING")
                && log.contains("commandId=" + followUp.commandId()));
        assertTrue(log.contains("operation=runtime_movement_player_roll")
                && log.contains("expected=final_ruling_and_follow_up_saved actual=completed")
                && log.contains("turnId=" + fixture.turnId));
        assertTrue(!log.contains("굴림을 제출해 주세요."));
        diagnosticLogger.detachAppender(diagnostics);
        diagnosticLogger.setLevel(previousLevel);
        if (priorDiagnostics == null) System.clearProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED");
        else System.setProperty("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", priorDiagnostics);
    }

    @Test
    void completed_typed_follow_up_is_not_repeated_when_local_commit_restarts() throws Exception {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED, List.of(), List.of(),
                null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED", null,
                new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                        followUpId(operationId), operationId, hostileTokenId, fixture.turnId,
                        com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                        "HOSTILE_OBSERVED", 4, List.of(), null, List.of("HOSTILE_OBSERVED")), null, hostileTokenId);
        RuntimeTurnCommand move = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        AtomicInteger publications = new AtomicInteger();
        String movementOutcome = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(movement);
        MovementFollowUpPort publisher = (command, adventureId, sessionId, ownerPlayerId) -> {
            publications.incrementAndGet();
            return fixture.followUpPublisher().publish(command, adventureId, sessionId, ownerPlayerId);
        };
        var firstInstance = fixture.orchestrator(ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, movementOutcome, movement), publisher);

        assertThrows(IllegalStateException.class, () -> firstInstance.commit(fixture.readyTurn(), List.of(move),
                () -> { throw new IllegalStateException("simulated process stop"); }));
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, fixture.commands.findByTurnId(fixture.turnId).stream()
                .filter(command -> command.commandType().equals("movement.follow-up")).findFirst().orElseThrow().executionStatus());
        assertEquals(1, publications.get());

        var restartedInstance = fixture.orchestrator(ignored -> {
            throw new AssertionError("saved movement must not run after restart");
        }, publisher);
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED,
                restartedInstance.resume(fixture.turnId, () -> {}).status());
        assertEquals(1, publications.get());
        assertEquals(RuntimeTurnLifecycle.COMMITTED, fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
    }

    @Test
    void map_ui_reuses_committed_movement_budget_when_turn_confirmation_save_fails() throws Exception {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var followUp = new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId(operationId), operationId, hostileTokenId, fixture.turnId,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED", 4, List.of(), null, List.of("HOSTILE_OBSERVED"));
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED, List.of(), List.of(),
                null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED", null, followUp, null, hostileTokenId);
        RuntimeTurnCommand move = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        String movementOutcome = fixture.objectMapper.writeValueAsString(movement);
        AtomicInteger movementExecutions = new AtomicInteger();
        AtomicInteger followUpPublications = new AtomicInteger();
        AtomicInteger localCommitCalls = new AtomicInteger();
        AtomicInteger persistedMovementBudget = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean localStateCommitted = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean loseTurnConfirmation = new java.util.concurrent.atomic.AtomicBoolean(true);
        RuntimeTurnRepository turnRepository = new RuntimeTurnRepository() {
            @Override public java.util.Optional<RuntimeTurn> findByTurnId(UUID id) { return fixture.turns.findByTurnId(id); }
            @Override public java.util.Optional<RuntimeTurn> findByCommandId(UUID id) { return fixture.turns.findByCommandId(id); }
            @Override public List<RuntimeTurn> findAllByAdventureId(com.dndmaster.adventure.domain.adventure.AdventureId id) {
                return fixture.turns.findAllByAdventureId(id);
            }
            @Override public void save(RuntimeTurn turn) {
                if (turn.lifecycle() == RuntimeTurnLifecycle.COMMITTED && loseTurnConfirmation.getAndSet(false))
                    throw new IllegalStateException("simulated process stop after movement budget commit");
                fixture.turns.save(turn);
            }
        };
        var publisher = (com.dndmaster.adventure.application.runtime.MovementFollowUpPort)
                (command, adventureId, sessionId, ownerPlayerId) -> {
                    followUpPublications.incrementAndGet();
                    return fixture.followUpPublisher().publish(command, adventureId, sessionId, ownerPlayerId);
                };
        RuntimeTurnCommandAdapter mapAdapter = command -> {
            movementExecutions.incrementAndGet();
            return RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, movementOutcome, movement);
        };
        RuntimeTurnCommitOrchestrator firstInstance = new RuntimeTurnCommitOrchestrator(turnRepository,
                fixture.commands, mapAdapter, publisher, fixture.followUpConsumer());
        Runnable localCommit = () -> {
            localCommitCalls.incrementAndGet();
            if (localStateCommitted.compareAndSet(false, true)) persistedMovementBudget.addAndGet(4);
        };

        assertThrows(IllegalStateException.class,
                () -> firstInstance.commit(fixture.readyTurn(), List.of(move), localCommit));
        assertEquals(4, persistedMovementBudget.get());
        assertEquals(RuntimeTurnLifecycle.COMMITTING, fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, fixture.commands.findByCommandId(move.commandId()).orElseThrow().executionStatus());

        RuntimeTurnCommitOrchestrator restartedInstance = new RuntimeTurnCommitOrchestrator(turnRepository,
                fixture.commands, ignored -> { throw new AssertionError("saved map movement must not run again"); },
                publisher, fixture.followUpConsumer());
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED,
                restartedInstance.resume(fixture.turnId, localCommit).status());
        assertEquals(4, persistedMovementBudget.get());
        assertEquals(2, localCommitCalls.get());
        assertEquals(1, movementExecutions.get());
        assertEquals(1, followUpPublications.get());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE,
                fixture.commands.findByCommandId(followUp.commandId()).orElseThrow().executionStatus());
        assertEquals(RuntimeTurnLifecycle.COMMITTED, fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
        assertEquals(fixture.turnId, fixture.objectMapper.readValue(
                fixture.commands.findByCommandId(followUp.commandId()).orElseThrow().payloadJson(),
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.class).turnId());
    }

    @Test
    void map_ui_reuses_saved_final_ruling_when_turn_completion_record_write_fails() throws Exception {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        var traversed = List.of(new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1));
        var finalPosition = traversed.getFirst();
        var followUp = new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                followUpId(operationId), operationId, hostileTokenId, fixture.turnId,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                "HOSTILE_OBSERVED", 4, traversed, finalPosition, List.of("HOSTILE_OBSERVED"));
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED,
                List.of(new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(1, 1),
                        new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(2, 1)),
                traversed, finalPosition, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED", null, followUp, null,
                hostileTokenId);
        RuntimeTurnCommand move = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        String movementOutcome = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(movement);
        AtomicInteger adjudications = new AtomicInteger();
        java.util.concurrent.atomic.AtomicBoolean failBeforeCommittedTurnSave = new java.util.concurrent.atomic.AtomicBoolean(true);
        RuntimeTurnRepository turnRepository = new RuntimeTurnRepository() {
            @Override public java.util.Optional<RuntimeTurn> findByTurnId(UUID id) { return fixture.turns.findByTurnId(id); }
            @Override public java.util.Optional<RuntimeTurn> findByCommandId(UUID id) { return fixture.turns.findByCommandId(id); }
            @Override public List<RuntimeTurn> findAllByAdventureId(com.dndmaster.adventure.domain.adventure.AdventureId id) {
                return fixture.turns.findAllByAdventureId(id);
            }
            @Override public void save(RuntimeTurn turn) {
                if (turn.lifecycle() == RuntimeTurnLifecycle.COMMITTED && failBeforeCommittedTurnSave.getAndSet(false)) {
                    throw new IllegalStateException("simulated failure before RuntimeTurn COMMITTED record write");
                }
                fixture.turns.save(turn);
            }
        };
        var consumer = new com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer(
                fixture.events, fixture.commands, fixture.objectMapper, (command, continuation) -> {
                    adjudications.incrementAndGet();
                    try {
                        return com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome.applied(
                                fixture.objectMapper.writeValueAsString(new com.dndmaster.adventure.application.runtime.CombatContinuationCommand(
                                        command.commandId(), continuation.turnId(), continuation.operationId(),
                                        continuation.hostileTokenId(), continuation.trigger(), continuation.kind(),
                                        "판정 완료", "다음 행동을 선택해 주세요.",
                                        com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.none(), false, false)));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                        throw new IllegalStateException(failure);
                    }
                });
        com.dndmaster.adventure.application.runtime.RuntimeTurnCommandAdapter firstAdapter = ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, movementOutcome, movement);
        var firstInstance = new RuntimeTurnCommitOrchestrator(turnRepository, fixture.commands, firstAdapter,
                fixture.followUpPublisher()::publish, consumer);

        assertThrows(IllegalStateException.class, () -> firstInstance.commit(fixture.readyTurn(), List.of(move), () -> {}));
        assertEquals(1, adjudications.get());
        RuntimeTurnCommand persistedFinalRuling = fixture.commands.findByTurnId(fixture.turnId).stream()
                .filter(command -> "movement.follow-up".equals(command.commandType())).findFirst().orElseThrow();
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, persistedFinalRuling.executionStatus());
        assertTrue(persistedFinalRuling.outcomeJson().contains("다음 행동을 선택해 주세요."));
        assertEquals(RuntimeTurnLifecycle.COMMITTING, fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());

        var restartedInstance = new RuntimeTurnCommitOrchestrator(turnRepository, fixture.commands,
                ignored -> { throw new AssertionError("saved movement must not run after restart"); },
                fixture.followUpPublisher()::publish, consumer);
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED,
                restartedInstance.resume(fixture.turnId, () -> {}).status());
        assertEquals(1, adjudications.get());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, persistedFinalRuling.executionStatus());
        assertEquals(RuntimeTurnLifecycle.COMMITTED, fixture.turns.findByTurnId(fixture.turnId).orElseThrow().lifecycle());
    }

    @Test
    void malformed_durable_follow_up_json_is_permanent_before_transient_retry_handling() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(fixture.turnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.follow-up", "{not-json", 0);

        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored -> {
            throw new AssertionError("malformed follow-up must not reach a port");
        }).commit(fixture.readyTurn(), List.of(command), () -> {
            throw new AssertionError("invalid follow-up must not commit the adventure");
        });

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.FAILED,
                fixture.commands.findByCommandId(command.commandId()).orElseThrow().executionStatus());
    }

    @Test
    void malformed_durable_follow_up_uuid_is_permanent_before_transient_retry_handling() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(fixture.turnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.follow-up",
                "{\"commandId\":\"not-a-uuid\",\"operationId\":\"not-a-uuid\"}", 0);

        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored -> {
            throw new AssertionError("malformed follow-up must not reach a port");
        }).commit(fixture.readyTurn(), List.of(command), () -> {
            throw new AssertionError("invalid follow-up must not commit the adventure");
        });

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
    }

    @Test
    void durable_follow_up_command_id_must_match_owning_runtime_command() throws Exception {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        UUID payloadCommandId = UUID.randomUUID();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(fixture.turnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.follow-up",
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                                payloadCommandId, operationId, hostileTokenId, fixture.turnId,
                                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                                "HOSTILE_OBSERVED")), 0);

        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored -> {
            throw new AssertionError("mismatched follow-up must not reach a port");
        }).commit(fixture.readyTurn(), List.of(command), () -> {
            throw new AssertionError("mismatched follow-up must not commit the adventure");
        });

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
    }

    @Test
    void durable_follow_up_turn_id_must_match_owning_runtime_command() throws Exception {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        RuntimeTurnCommand command = RuntimeTurnCommand.create(fixture.turnId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.follow-up",
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                        new com.dndmaster.adventure.application.combat.MovementFollowUpCommand(
                                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.Kind.COMBAT,
                                "HOSTILE_OBSERVED")), 0);

        RuntimeTurnCommitOrchestrator.Result result = fixture.orchestrator(ignored -> {
            throw new AssertionError("mismatched follow-up must not reach a port");
        }).commit(fixture.readyTurn(), List.of(command), () -> {
            throw new AssertionError("mismatched follow-up must not commit the adventure");
        });

        assertEquals(RuntimeTurnCommitOrchestrator.Status.REPAIR_REQUIRED, result.status());
    }

    @Test
    void application_service_forward_recovers_map_failure_before_committing_adventure_state() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        RuntimeTurn ready = fixture.readyTurn();
        RuntimeTurnCommand command = fixture.command("combat-map.move", 0, RuntimeTurnCommand.ExecutionStatus.PENDING);
        fixture.turns.save(ready.beginCommit());
        var adventureRepository = mock(AdventureRepository.class);
        Adventure adventure = mock(Adventure.class);
        when(adventureRepository.findById(ready.adventureId())).thenReturn(Optional.of(adventure));
        when(adventure.version()).thenReturn(ready.version());
        when(adventure.ownerPlayerId()).thenReturn(new OwnerPlayerId(UUID.randomUUID()));
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, UUID.randomUUID(),
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.COMMITTED, List.of(),
                new com.dndmaster.adventure.application.combat.CombatMapPreviewPosition(2, 1), List.of(), null);
        var service = new RuntimeTurnApplicationService(adventureRepository, mock(RuntimeBindingRepository.class),
                mock(ScenarioPackageRepository.class), fixture.turns, mock(RuntimeEvidenceSearchPort.class),
                mock(RuntimePlanningPort.class), mock(NarrationSafetyPort.class), mock(SessionKnowledgeSetRepository.class));
        service.setCommitOrchestrator(fixture.orchestrator(ignored ->
                RuntimeTurnCommandExecution.movement(RuntimeTurnCommandExecution.Status.DONE, "saved", movement)));
        fixture.commands.save(command);

        var result = service.resumeRuntimeTurn(ready.turnId());

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, result.status());
        assertEquals(RuntimeTurnLifecycle.COMMITTED, result.turn().lifecycle());
        assertEquals(movement, result.movementResult());
        verify(adventure).commitRuntimeTurn(any(OwnerPlayerId.class), eq(ready.version()), eq(ready.pendingState()),
                eq(ready.context()), eq(List.of(
                        new com.dndmaster.adventure.domain.adventure.ConversationEntry(0, "PLAYER", ready.action()),
                        new com.dndmaster.adventure.domain.adventure.ConversationEntry(1, "AI_GAME_MASTER", ready.narration()))),
                eq(ready.completionProposal()));
        verify(adventureRepository).save(adventure);
    }

    private static final class RuntimeTurnFixture {
        private final UUID turnId = UUID.randomUUID();
        private final InMemoryRuntimeTurnRepository turns = new InMemoryRuntimeTurnRepository();
        private final InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();
        private final InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        MovementFollowUpEventPublisher followUpPublisher() {
            return new MovementFollowUpEventPublisher(events, objectMapper);
        }

        MovementFollowUpRuntimeConsumer followUpConsumer() {
            return new MovementFollowUpRuntimeConsumer(events, commands, objectMapper,
                    (command, continuation) -> {
                        try {
                            return com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome.applied(
                                    objectMapper.writeValueAsString(new com.dndmaster.adventure.application.runtime.CombatContinuationCommand(
                                            command.commandId(), continuation.turnId(), continuation.operationId(),
                                            continuation.hostileTokenId(), continuation.trigger(), continuation.kind(),
                                            "판정 완료", "다음 행동을 선택해 주세요.",
                                            com.dndmaster.adventure.application.runtime.RuntimeCheckProposal.none(), false, false)));
                        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                            throw new IllegalStateException(failure);
                        }
                    });
        }

        RuntimeTurn readyTurn() {
            UUID commandId = UUID.randomUUID();
            UUID adventureId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            UUID packageId = UUID.randomUUID();
            var evidence = new com.dndmaster.adventure.application.runtime.EvidencePack(List.of(), List.of(), List.of());
            var plan = new com.dndmaster.adventure.application.runtime.RuntimePlan(
                    "scene", "npc", "judgment", "narration", null, List.of(), List.of());
            var base = new RuntimeTurn(turnId, commandId, new com.dndmaster.adventure.domain.adventure.AdventureId(adventureId),
                    sessionId, packageId, 1, "action", evidence, plan, null,
                    new com.dndmaster.adventure.domain.adventure.AdventureContext("scene", "npc", "action", "judgment"),
                    List.of(), 0, List.of(), List.of(), false, false,
                    com.dndmaster.adventure.application.runtime.RuntimeTurnOrigin.GM, false);
            var pending = new com.dndmaster.adventure.domain.runtime.PendingRuntimeState(
                    com.dndmaster.adventure.domain.runtime.GameStateDelta.empty(),
                    com.dndmaster.adventure.domain.runtime.DisclosureState.empty(),
                    com.dndmaster.adventure.domain.runtime.CurrentSituation.initial("problem"), List.of());
            return base.asRequested().beginResolving()
                    .fixResolution(new com.dndmaster.adventure.application.runtime.RuntimeTurnResolution("ok", null, List.of()), pending,
                            com.dndmaster.adventure.domain.runtime.CompletionProposal.continueAdventure())
                    .beginNarration().beginSafetyCheck().readyToCommit("safe");
        }

        RuntimeTurnCommand command(String type, int order, RuntimeTurnCommand.ExecutionStatus status) {
            return RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "external", type, "{}", order).withStatus(status);
        }

        RuntimeTurnCommitOrchestrator orchestrator(RuntimeTurnCommandAdapter adapter) {
            return new RuntimeTurnCommitOrchestrator(turns, commands, adapter, followUpPublisher()::publish,
                    followUpConsumer());
        }

        RuntimeTurnCommitOrchestrator orchestrator(RuntimeTurnCommandAdapter adapter,
                com.dndmaster.adventure.application.runtime.MovementFollowUpPort followUpPort) {
            return new RuntimeTurnCommitOrchestrator(turns, commands, adapter, followUpPort, followUpConsumer());
        }
    }
}
