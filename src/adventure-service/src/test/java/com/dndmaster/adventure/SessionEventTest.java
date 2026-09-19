package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.InMemorySessionEventRepository;
import com.dndmaster.adventure.application.runtime.MovementFollowUpEventPublisher;
import com.dndmaster.adventure.application.runtime.MovementFollowUpPort;
import com.dndmaster.adventure.application.runtime.SessionEventRepository;
import com.dndmaster.adventure.application.runtime.InMemoryRuntimeTurnCommandRepository;
import com.dndmaster.adventure.application.runtime.MovementFollowUpRuntimeConsumer;
import com.dndmaster.adventure.application.runtime.RuntimeTurnCommand;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationHandlerRegistry;
import com.dndmaster.adventure.application.runtime.RuntimeContinuationOutcome;
import com.dndmaster.adventure.application.runtime.MovementFollowUpPolicy;
import com.dndmaster.adventure.application.combat.MovementFollowUpCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import com.dndmaster.adventure.domain.runtime.event.SessionEvent;
import java.util.UUID;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
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
        MovementFollowUpCommand first = MovementFollowUpCommand.hostileObserved(UUID.randomUUID());
        MovementFollowUpCommand second = new MovementFollowUpCommand(UUID.randomUUID(), UUID.randomUUID(),
                MovementFollowUpCommand.Kind.WARNING, "FEATURE_REVEALED");

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
        MovementFollowUpCommand followUp = new MovementFollowUpCommand(UUID.randomUUID(), UUID.randomUUID(),
                MovementFollowUpCommand.Kind.CONTINUATION, "NPC_CONTACT");

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
                return publisher.publish(MovementFollowUpCommand.hostileObserved(UUID.randomUUID()), adventure,
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
        MovementFollowUpCommand followUp = MovementFollowUpCommand.hostileObserved(UUID.randomUUID());
        RuntimeTurnCommand source = RuntimeTurnCommand.create(UUID.randomUUID(), followUp.commandId(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "external", "movement.follow-up", "{}", 1);
        new MovementFollowUpEventPublisher(events, new ObjectMapper()).publish(followUp, source.adventureId(),
                source.sessionId(), source.ownerPlayerId());
        MovementFollowUpRuntimeConsumer consumer = new MovementFollowUpRuntimeConsumer(events, commands,
                new ObjectMapper(), trigger -> MovementFollowUpCommand.Kind.DIALOGUE);

        assertEquals(MovementFollowUpPort.Result.Status.DONE, consumer.consume(source, followUp).status());
        assertEquals(MovementFollowUpPort.Result.Status.DONE, consumer.consume(source, followUp).status());
        var continuation = commands.findByTurnId(source.turnId()).stream()
                .filter(command -> command.commandType().startsWith("movement.continuation.")).toList();
        assertEquals(1, continuation.size());
        assertEquals("movement.continuation.dialogue", continuation.getFirst().commandType());
        assertEquals(RuntimeTurnCommand.ExecutionStatus.DONE, continuation.getFirst().executionStatus());
        assertEquals("DIALOGUE:transitioned:" + followUp.operationId(), continuation.getFirst().outcomeJson());
    }

    @Test
    void runtime_calls_the_typed_handler_and_retries_a_failed_transition_idempotently() {
        InMemorySessionEventRepository events = new InMemorySessionEventRepository();
        InMemoryRuntimeTurnCommandRepository commands = new InMemoryRuntimeTurnCommandRepository();
        MovementFollowUpCommand followUp = MovementFollowUpCommand.hostileObserved(UUID.randomUUID());
        RuntimeTurnCommand source = RuntimeTurnCommand.create(UUID.randomUUID(), followUp.commandId(), UUID.randomUUID(),
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
}
