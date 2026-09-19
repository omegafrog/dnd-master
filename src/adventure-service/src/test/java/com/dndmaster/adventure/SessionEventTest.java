package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.runtime.InMemorySessionEventRepository;
import com.dndmaster.adventure.application.runtime.MovementFollowUpEventPublisher;
import com.dndmaster.adventure.application.runtime.MovementFollowUpPort;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.runtime.InMemoryRuntimeTurnCommandRepository;
import com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationHandlerRegistry;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationCommandPort;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationCommandOutcome;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationCommandOutcomePort;
import com.dndmaster.adventure.application.runtime.PostgresRuntimeContinuationCommandOutcomePort;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandAdapterRegistry;
import com.dndmaster.adventure.application.runtime.TypedRuntimeContinuationCommandAdapter;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution;
import com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import java.util.UUID;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SessionEventTest {
    @Test
    void event_versions_are_monotonic_and_duplicate_safe() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        UUID session = UUID.randomUUID();
        SessionEvent event = new SessionEvent(session, UUID.randomUUID(), 7, "GM_TURN_COMMITTED", "result");
        events.append(event);
        events.append(event);
        assertEquals(1, events.after(session, 0).size());
        assertEquals(0, events.after(session, 7).size());
    }

    @Test
    void movement_follow_ups_allocate_monotonic_versions_and_retry_conflicts_without_loss() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        MovementFollowUpEventPublisher publisher = new MovementFollowUpEventPublisher(events, new ObjectMapper());
        UUID session = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        MovementFollowUpCommand first = MovementFollowUpCommand.hostileObserved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        MovementFollowUpCommand second = new MovementFollowUpCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), MovementFollowUpCommand.Kind.WARNING, "FEATURE_REVEALED");

        assertEquals("COMBAT", publisher.publish(first, adventure, session, owner).value());
        assertEquals("WARNING", publisher.publish(second, adventure, session, owner).value());
        assertEquals("COMBAT", publisher.publish(first, adventure, session, owner).value());
        assertEquals(List.of(0L, 1L), events.after(session, -1).stream().map(SessionEvent::version).toList());
        assertEquals(List.of(first.commandId(), second.commandId()),
                events.after(session, -1).stream().map(SessionEvent::eventId).toList());
    }

    @Test
    void movement_follow_up_recalculates_the_version_after_a_conflict() {
        InMemorySessionEventRepository stored = new InMemorySessionEventRepository();
        SessionEventRepository conflicting = new SessionEventRepository() {
            private boolean conflict = true;
            @Override public void append(SessionEvent event) {
                if (conflict) {
                    conflict = false;
                    stored.append(new SessionEvent(event.sessionId(), UUID.randomUUID(), event.version(),
                            "OTHER_EVENT", "other"));
                }
                stored.append(event);
            }
            @Override public SessionEvent appendNext(UUID sessionId, UUID eventId, String type, String payload) {
                if (conflict) {
                    conflict = false;
                    stored.append(new SessionEvent(sessionId, UUID.randomUUID(), 0, "OTHER_EVENT", "other"));
                    throw new IllegalStateException("session event version conflict");
                }
                long next = stored.after(sessionId, -1).stream().mapToLong(SessionEvent::version).max().orElse(-1) + 1;
                SessionEvent event = new SessionEvent(sessionId, eventId, next, type, payload);
                stored.append(event);
                return event;
            }
            @Override public List<SessionEvent> after(UUID sessionId, long version) { return stored.after(sessionId, version); }
        };
        MovementFollowUpCommand followUp = new MovementFollowUpCommand(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), MovementFollowUpCommand.Kind.CONTINUATION, "NPC_CONTACT");

        MovementFollowUpPort.Result result = new MovementFollowUpEventPublisher(conflicting, new ObjectMapper()).publish(
                followUp, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertEquals(MovementFollowUpPort.Result.Status.DONE, result.status());
    }

    @Test
    void concurrent_follow_ups_reserve_every_session_version_once() throws Exception {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        MovementFollowUpEventPublisher publisher = new MovementFollowUpEventPublisher(events, new ObjectMapper());
        UUID session = UUID.randomUUID();
        UUID adventure = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        int count = 32;
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = java.util.stream.IntStream.range(0, count).mapToObj(index -> executor.submit(() -> {
                return publisher.publish(MovementFollowUpCommand.hostileObserved(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()), adventure,
                        session, owner);
            })).toList();
            for (var future : futures) assertEquals(MovementFollowUpPort.Result.Status.DONE, future.get().status());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(java.util.stream.LongStream.range(0, count).boxed().toList(),
                events.after(session, -1).stream().map(SessionEvent::version).toList());
    }

    @Test
    void runtime_consumes_durable_follow_up_into_one_typed_continuation_and_replays_it_idempotently() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();
        UUID turnId = UUID.randomUUID();
        MovementFollowUpCommand followUp = MovementFollowUpCommand.hostileObserved(UUID.randomUUID(), turnId, UUID.randomUUID());
        RuntimeTurnCommand source = RuntimeTurnCommand.create(turnId, followUp.commandId(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.follow-up", "{}", 1);
        new MovementFollowUpEventPublisher(events, new ObjectMapper()).publish(followUp, source.adventureId(),
                source.sessionId(), source.ownerPlayerId());
        MovementFollowUpRuntimeConsumer consumer = new MovementFollowUpRuntimeConsumer(events, commands,
                new ObjectMapper(), trigger -> MovementFollowUpCommand.Kind.DIALOGUE,
                RuntimeContinuationHandlerRegistry.standard(command -> {
                    assertEquals("movement.continuation.dialogue", command.commandType());
                    return com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution.done(
                            "dialogue-state:" + command.commandId());
                }));

        assertEquals(MovementFollowUpPort.Result.Status.DONE, consumer.consume(source, followUp).status());
        assertEquals(MovementFollowUpPort.Result.Status.DONE, consumer.consume(source, followUp).status());
        var continuation = commands.findByTurnId(source.turnId()).stream()
                .filter(command -> command.commandType().startsWith("movement.continuation.")).toList();
        assertEquals(1, continuation.size());
        assertEquals("movement.continuation.dialogue", continuation.getFirst().commandType());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, continuation.getFirst().executionStatus());
        assertEquals("dialogue-state:" + continuation.getFirst().commandId(), continuation.getFirst().outcomeJson());
    }

    @Test
    void standard_continuation_retries_when_the_runtime_adapter_does_not_report_a_transition() {
        AtomicReference<RuntimeTurnCommand> dispatched = new AtomicReference<>();
        RuntimeContinuationHandlerRegistry registry = RuntimeContinuationHandlerRegistry.standard(command -> {
            dispatched.set(command);
            return com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution.transientFailure("runtime unavailable");
        });
        RuntimeTurnCommand command = RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.continuation.combat", "{}", 1);

        RuntimeContinuationOutcome outcome = registry.execute(command,
                new MovementFollowUpRuntimeConsumer.Continuation(MovementFollowUpCommand.Kind.COMBAT,
                        "HOSTILE_OBSERVED", UUID.randomUUID(), UUID.randomUUID()));

        assertEquals(RuntimeContinuationOutcome.Status.RETRY, outcome.status());
        assertEquals(command, dispatched.get());
        assertTrue(outcome.value().contains("runtime unavailable"));
    }

    @Test
    void runtime_calls_the_typed_handler_and_retries_a_failed_transition_idempotently() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();
        UUID turnId = UUID.randomUUID();
        MovementFollowUpCommand followUp = MovementFollowUpCommand.hostileObserved(UUID.randomUUID(), turnId, UUID.randomUUID());
        RuntimeTurnCommand source = RuntimeTurnCommand.create(turnId, followUp.commandId(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.follow-up", "{}", 1);
        new MovementFollowUpEventPublisher(events, new ObjectMapper()).publish(followUp, source.adventureId(),
                source.sessionId(), source.ownerPlayerId());

        AtomicInteger calls = new AtomicInteger();
        EnumMap<MovementFollowUpCommand.Kind, com.dndmaster.adventure.application.runtime.RuntimeContinuationHandler> handlers =
                new EnumMap<>(MovementFollowUpCommand.Kind.class);
        handlers.put(MovementFollowUpCommand.Kind.COMBAT, (command, continuation) -> {
            if (calls.getAndIncrement() == 0) return RuntimeContinuationOutcome.retry("combat temporarily unavailable");
            return RuntimeContinuationOutcome.applied("COMBAT:state-changed");
        });
        MovementFollowUpRuntimeConsumer consumer = new MovementFollowUpRuntimeConsumer(events, commands,
                new ObjectMapper(), MovementFollowUpPolicy.defaultPolicy(),
                new RuntimeContinuationHandlerRegistry(handlers));

        assertEquals(MovementFollowUpPort.Result.Status.RETRY, consumer.consume(source, followUp).status());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.FAILED,
                commands.findByTurnId(source.turnId()).getFirst().executionStatus());
        MovementFollowUpPort.Result second = consumer.consume(source, followUp);
        org.junit.jupiter.api.Assertions.assertTrue(second.status() == MovementFollowUpPort.Result.Status.DONE,
                second.status() + ":" + second.value() + ":calls=" + calls.get());
        RuntimeTurnCommand continuation = commands.findByTurnId(source.turnId()).getFirst();
        assertEquals(2, calls.get());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, continuation.executionStatus());
        assertEquals("COMBAT:state-changed", continuation.outcomeJson());
        assertEquals(MovementFollowUpPort.Result.Status.DONE, consumer.consume(source, followUp).status());
        assertEquals(2, calls.get());
    }

    @Test
    void registered_continuation_adapters_are_wired_without_gm_fallback() {
        RuntimeContinuationCommandPort port = new RuntimeContinuationCommandPort() {
            @Override public com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution combat(ContinuationCommand command) { return RuntimeTurnCommandExecution.done("combat"); }
            @Override public com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution warning(ContinuationCommand command) { return RuntimeTurnCommandExecution.done("warning"); }
            @Override public com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution dialogue(ContinuationCommand command) { return RuntimeTurnCommandExecution.done("dialogue"); }
            @Override public com.dndmaster.adventure.application.runtime.RuntimeTurnCommandExecution chase(ContinuationCommand command) { return RuntimeTurnCommandExecution.done("chase"); }
        };
        RuntimeTurnCommandAdapterRegistry adapters = new RuntimeTurnCommandAdapterRegistry(java.util.Map.of(
                "movement.continuation.combat", new TypedRuntimeContinuationCommandAdapter(TypedRuntimeContinuationCommandAdapter.Kind.COMBAT, port),
                "movement.continuation.warning", new TypedRuntimeContinuationCommandAdapter(TypedRuntimeContinuationCommandAdapter.Kind.WARNING, port),
                "movement.continuation.dialogue", new TypedRuntimeContinuationCommandAdapter(TypedRuntimeContinuationCommandAdapter.Kind.DIALOGUE, port),
                "movement.continuation.chase", new TypedRuntimeContinuationCommandAdapter(TypedRuntimeContinuationCommandAdapter.Kind.CHASE, port)));

        for (MovementFollowUpCommand.Kind kind : List.of(MovementFollowUpCommand.Kind.COMBAT,
                MovementFollowUpCommand.Kind.WARNING, MovementFollowUpCommand.Kind.DIALOGUE, MovementFollowUpCommand.Kind.CHASE)) {
            UUID turnId = UUID.randomUUID();
            UUID operationId = UUID.randomUUID();
            String payload = "{\"kind\":\"" + kind + "\",\"trigger\":\"HOSTILE_OBSERVED\",\"operationId\":\""
                    + operationId + "\",\"turnId\":\"" + turnId + "\"}";
            RuntimeTurnCommand command = RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), "external", "movement.continuation." + kind.name().toLowerCase(), payload, 1);
            RuntimeContinuationOutcome outcome = RuntimeContinuationHandlerRegistry.standard(adapters).execute(command,
                    new MovementFollowUpRuntimeConsumer.Continuation(kind, "HOSTILE_OBSERVED", operationId, turnId));
            assertEquals(RuntimeContinuationOutcome.Status.APPLIED, outcome.status());
        }
    }

    @Test
    void unknown_movement_trigger_is_rejected_as_a_permanent_failure() {
        assertThrows(IllegalArgumentException.class,
                () -> MovementFollowUpPolicy.defaultPolicy().determine("UNSUPPORTED_TRIGGER"));
    }

    @Test
    void unknown_runtime_command_type_is_a_permanent_explicit_failure() {
        RuntimeTurnCommandAdapterRegistry adapters = new RuntimeTurnCommandAdapterRegistry(java.util.Map.of());
        RuntimeTurnCommand command = RuntimeTurnCommand.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.continuation.unknown", "{}", 1);

        RuntimeTurnCommandExecution result = adapters.execute(command);

        assertEquals(RuntimeTurnCommandExecution.Status.PERMANENT_FAILURE, result.status());
        assertEquals("unknown runtime command type: movement.continuation.unknown", result.value());
    }

    @Test
    void missing_follow_up_identity_fields_are_permanent_failures_not_retries() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();
        ObjectMapper mapper = new ObjectMapper();
        UUID turnId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        RuntimeTurnCommand source = RuntimeTurnCommand.create(turnId, commandId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "external", "movement.follow-up", "{}", 1);
        events.append(new SessionEvent(source.sessionId(), commandId, 0, "MOVEMENT_FOLLOW_UP",
                "{\"commandId\":\"" + commandId + "\",\"operationId\":\"" + operationId
                        + "\",\"turnId\":\"" + turnId + "\",\"kind\":\"COMBAT\",\"trigger\":\"HOSTILE_OBSERVED\"}"));
        MovementFollowUpCommand expected = MovementFollowUpCommand.hostileObserved(operationId, turnId, UUID.randomUUID());
        MovementFollowUpCommand expectedWithCommandId = new MovementFollowUpCommand(commandId, expected.operationId(),
                expected.hostileTokenId(), expected.turnId(), expected.kind(), expected.trigger());
        MovementFollowUpRuntimeConsumer consumer = new MovementFollowUpRuntimeConsumer(events, commands, mapper,
                MovementFollowUpPolicy.defaultPolicy(), (command, continuation) -> RuntimeContinuationOutcome.applied("done"));

        MovementFollowUpPort.Result result = consumer.consume(source, expectedWithCommandId);

        assertEquals(MovementFollowUpPort.Result.Status.PERMANENT_FAILURE, result.status());
        assertTrue(result.value().contains("hostileTokenId"));
    }

    @Test
    void missing_follow_up_turn_id_is_a_permanent_failure_not_a_retry() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();
        UUID turnId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();
        RuntimeTurnCommand source = RuntimeTurnCommand.create(turnId, commandId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "external", "movement.follow-up", "{}", 1);
        events.append(new SessionEvent(source.sessionId(), commandId, 0, "MOVEMENT_FOLLOW_UP",
                "{\"commandId\":\"" + commandId + "\",\"operationId\":\"" + operationId
                        + "\",\"hostileTokenId\":\"" + hostileTokenId
                        + "\",\"kind\":\"COMBAT\",\"trigger\":\"HOSTILE_OBSERVED\"}"));
        MovementFollowUpCommand expected = new MovementFollowUpCommand(commandId, operationId, hostileTokenId, turnId,
                MovementFollowUpCommand.Kind.COMBAT, "HOSTILE_OBSERVED");
        MovementFollowUpRuntimeConsumer consumer = new MovementFollowUpRuntimeConsumer(events, commands, new ObjectMapper(),
                MovementFollowUpPolicy.defaultPolicy(), (command, continuation) -> RuntimeContinuationOutcome.applied("done"));

        MovementFollowUpPort.Result result = consumer.consume(source, expected);

        assertEquals(MovementFollowUpPort.Result.Status.PERMANENT_FAILURE, result.status());
        assertTrue(result.value().contains("turnId"));
    }

    @Test
    void each_continuation_kind_persists_a_distinct_runtime_command_payload_and_replays_it() {
        InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();
        RuntimeContinuationCommandOutcomePort outcomes = new PostgresRuntimeContinuationCommandOutcomePort(commands, new ObjectMapper());
        UUID turnId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID hostileTokenId = UUID.randomUUID();

        for (MovementFollowUpCommand.Kind kind : List.of(MovementFollowUpCommand.Kind.COMBAT,
                MovementFollowUpCommand.Kind.WARNING, MovementFollowUpCommand.Kind.DIALOGUE,
                MovementFollowUpCommand.Kind.CHASE)) {
            RuntimeTurnCommand command = RuntimeTurnCommand.create(turnId, UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), "external",
                    "movement.continuation." + kind.name().toLowerCase(), "{}", kind.ordinal());
            RuntimeContinuationCommandPort.ContinuationCommand typed = new RuntimeContinuationCommandPort.ContinuationCommand(
                    command, new MovementFollowUpRuntimeConsumer.Continuation(kind, "HOSTILE_OBSERVED", operationId,
                            turnId, hostileTokenId));
            RuntimeContinuationCommandOutcome first = switch (kind) {
                case COMBAT -> outcomes.combat(typed);
                case WARNING -> outcomes.warning(typed);
                case DIALOGUE -> outcomes.dialogue(typed);
                case CHASE -> outcomes.chase(typed);
                default -> throw new AssertionError(kind);
            };
            RuntimeContinuationCommandOutcome second = switch (kind) {
                case COMBAT -> outcomes.combat(typed);
                case WARNING -> outcomes.warning(typed);
                case DIALOGUE -> outcomes.dialogue(typed);
                case CHASE -> outcomes.chase(typed);
                default -> throw new AssertionError(kind);
            };
            assertEquals(first, second);
            assertEquals(turnId, first.turnId());
            assertEquals(operationId, first.operationId());
            assertEquals(hostileTokenId, first.hostileTokenId());
            assertEquals(kind.name().substring(0, 1) + kind.name().substring(1).toLowerCase(),
                    first.getClass().getSimpleName().replace("ContinuationCommand", ""));
            assertTrue(commands.findByCommandId(command.commandId()).orElseThrow().outcomeJson().contains("HOSTILE_OBSERVED"));
        }
    }
}
