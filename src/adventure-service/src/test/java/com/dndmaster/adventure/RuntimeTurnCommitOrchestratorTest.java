package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

class RuntimeTurnCommitOrchestratorTest {
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
                "HOSTILE_OBSERVED");
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(
                3, operationId, com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED,
                List.of(), List.of(), null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED",
                null, followUp, null, hostileTokenId);
        String rawFollowUp = "{\"commandId\":\"" + followUpId + "\",\"operationId\":\"" + operationId
                + "\",\"hostileTokenId\":\"" + hostileTokenId + "\",\"turnId\":\"" + fixture.turnId
                + "\",\"kind\":\"COMBAT\",\"trigger\":\"HOSTILE_OBSERVED\"}";
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

        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED, result.status());
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
    void restores_the_saved_movement_result_when_a_saga_resumes_after_the_command_is_done() {
        RuntimeTurnFixture fixture = new RuntimeTurnFixture();
        UUID operationId = UUID.randomUUID();
        String outcome = "{\"version\":4,\"operationId\":\"" + operationId
                + "\",\"status\":\"INTERRUPTED\",\"requestedPath\":[{\"x\":1,\"y\":1},{\"x\":2,\"y\":1}],"
                + "\"traversedPath\":[{\"x\":1,\"y\":1}],\"finalPosition\":{\"x\":1,\"y\":1},"
                + "\"publicEvents\":[\"FEATURE_REVEALED\"],\"interruptionReason\":\"FEATURE_REVEALED\"}";
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
        var movement = new com.dndmaster.adventure.application.combat.CombatMapMoveResult(4, operationId,
                com.dndmaster.adventure.application.combat.CombatMapMovementStatus.INTERRUPTED, List.of(), List.of(),
                null, List.of("HOSTILE_OBSERVED"), "HOSTILE_OBSERVED", null,
                com.dndmaster.adventure.application.combat.MovementFollowUpCommand.hostileObserved(operationId,
                        fixture.turnId, UUID.randomUUID()), null);
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

        assertEquals(RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED,
                orchestrator.commit(fixture.readyTurn(), List.of(move), () -> {}).status());
        assertEquals(RuntimeTurnCommitOrchestrator.Status.COMMITTED,
                orchestrator.resume(fixture.turnId, () -> {}).status());
        assertEquals(2, publications.get());
        assertEquals(1, fixture.commands.findByTurnId(fixture.turnId).stream()
                .filter(command -> command.commandType().equals("movement.follow-up")).count());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, fixture.commands.findByTurnId(fixture.turnId).stream()
                .filter(command -> command.commandType().equals("movement.follow-up")).findFirst().orElseThrow().executionStatus());
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
                eq(ready.context()), eq(ready.conversation()), eq(ready.completionProposal()));
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
                    (command, continuation) -> com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome.applied("continued"));
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
            var pending = new com.dndmaster.adventure.application.runtime.PendingRuntimeState(
                    com.dndmaster.adventure.domain.runtime.GameStateDelta.empty(),
                    com.dndmaster.adventure.domain.runtime.DisclosureState.empty(),
                    com.dndmaster.adventure.domain.runtime.CurrentSituation.initial("problem"), List.of());
            return base.asRequested().beginResolving()
                    .fixResolution(new com.dndmaster.adventure.application.runtime.RuntimeTurnResolution("ok", null, List.of()), pending,
                            com.dndmaster.adventure.application.runtime.CompletionProposal.continueAdventure())
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
